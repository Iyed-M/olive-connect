package tn.oliveconnect.mill;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.oliveconnect.common.BusinessRuleException;
import tn.oliveconnect.common.ConflictException;
import tn.oliveconnect.common.NotFoundException;
import tn.oliveconnect.harvest.HarvestRequest;
import tn.oliveconnect.harvest.HarvestRequestRepository;
import tn.oliveconnect.resource.Bin;
import tn.oliveconnect.resource.Equipment;
import tn.oliveconnect.resource.EquipmentRepository;
import tn.oliveconnect.round.CollectionRound;
import tn.oliveconnect.round.CollectionRoundDto;
import tn.oliveconnect.round.CollectionRoundRepository;
import tn.oliveconnect.round.RoundBinDto;
import tn.oliveconnect.round.RoundStop;
import tn.oliveconnect.round.RoundStatus;
import tn.oliveconnect.notification.NotificationService;
import tn.oliveconnect.notification.NotificationType;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

@Service
@Transactional
public class MillReceptionService {

    private static final double MAX_BIN_FILL_RATE = 1.15;

    private final MillReceptionRepository receptionRepository;
    private final CollectionRoundRepository roundRepository;
    private final HarvestRequestRepository harvestRequestRepository;
    private final EquipmentRepository equipmentRepository;
    private final MillRepository millRepository;
    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public MillReceptionService(MillReceptionRepository receptionRepository,
                                CollectionRoundRepository roundRepository,
                                HarvestRequestRepository harvestRequestRepository,
                                EquipmentRepository equipmentRepository,
                                MillRepository millRepository,
                                MongoTemplate mongoTemplate,
                                UserRepository userRepository,
                                NotificationService notificationService) {
        this.receptionRepository = receptionRepository;
        this.roundRepository = roundRepository;
        this.harvestRequestRepository = harvestRequestRepository;
        this.equipmentRepository = equipmentRepository;
        this.millRepository = millRepository;
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public MillDto publishCapacity(String millId, MillCapacityDto dto, Authentication authentication) {
        requireMillAccess(millId, authentication);
        Mill mill = findMill(millId);
        mill.setAvailableCapacityKg(dto.availableCapacityKg());
        mill.setCapacityUpdatedAt(Instant.now());
        return MillDto.from(millRepository.save(mill));
    }

    @Transactional(readOnly = true)
    public List<CollectionRoundDto> incoming(String millId, Authentication authentication) {
        requireMillManagerAccess(millId, authentication);
        return roundRepository.findByMillIdAndDateOrderByStartTimeAsc(millId, LocalDate.now(ZoneOffset.UTC)).stream()
                .filter(round -> round.getStatus() != RoundStatus.CANCELLED)
                .map(round -> CollectionRoundDto.from(round, List.of(), binsFor(round)))
                .toList();
    }

    public MillReceptionDto record(String millId, MillReceptionCreateDto dto, Authentication authentication) {
        requireMillManagerAccess(millId, authentication);
        Mill mill = findMill(millId);
        CollectionRound round = roundRepository.findById(dto.roundId())
                .orElseThrow(() -> new NotFoundException("Collection round not found"));
        if (!Objects.equals(millId, round.getMillId())) {
            throw new BusinessRuleException("ROUND_MILL_MISMATCH", "The round does not deliver to this mill");
        }

        RoundStop stop = round.getStops().stream()
                .filter(candidate -> Objects.equals(candidate.getOrchardId(), dto.orchardId()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("ORCHARD_NOT_IN_ROUND",
                        "The orchard is not part of the selected round"));
        if (!round.getEquipmentIds().contains(dto.binId())) {
            throw new BusinessRuleException("BIN_NOT_IN_ROUND", "The bin is not part of the selected round");
        }
        Equipment equipment = equipmentRepository.findById(dto.binId())
                .orElseThrow(() -> new BusinessRuleException("BIN_NOT_IN_ROUND", "The bin is not part of the selected round"));
        if (!(equipment instanceof Bin bin)) {
            throw new BusinessRuleException("BIN_NOT_IN_ROUND", "The selected equipment is not a bin");
        }

        validateWeights(dto.grossWeightKg(), dto.tareKg(), bin.getCapacityKg());
        if (receptionRepository.existsByRoundIdAndBinIdAndOrchardId(dto.roundId(), dto.binId(), dto.orchardId())) {
            throw new ConflictException("RECEPTION_EXISTS", "This bin load was already received");
        }

        HarvestRequest request = harvestRequestRepository.findById(stop.getHarvestRequestId())
                .orElseThrow(() -> new NotFoundException("Harvest request not found"));
        Instant receivedAt = Instant.now();
        MillReception reception = newReception(millId, dto, bin, request, receivedAt, authentication.getName());
        try {
            MillReception saved = receptionRepository.save(reception);
            var capacityUpdate = mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(millId)),
                    new Update().inc("availableCapacityKg", -saved.getNetWeightKg())
                            .set("capacityUpdatedAt", receivedAt),
                    Mill.class);
            if (capacityUpdate.getMatchedCount() == 0) {
                throw new NotFoundException("Mill not found");
            }
            Mill updatedMill = findMill(millId);
            if (updatedMill.getAvailableCapacityKg() < 0) {
                notificationService.notifyCollectionManagers(NotificationType.MILL_SATURATED,
                        "Mill capacity exceeded", "The available capacity of " + updatedMill.getName()
                                + " is below zero after a reception", "/mills/" + millId);
            }
            notificationService.notifyUser(saved.getFarmerId(), NotificationType.RECEPTION_RECORDED,
                    "Reception recorded", "Your olives were received: " + saved.getNetWeightKg()
                            + " kg, grade " + saved.getQuality().getGrade(), "/farmer/receptions");
            return MillReceptionDto.from(saved);
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("RECEPTION_EXISTS", "This bin load was already received");
        }
    }

    @Transactional(readOnly = true)
    public List<MillReceptionDto> list(Boolean mine, Authentication authentication) {
        List<MillReception> receptions;
        if (hasRole(authentication, Role.COLLECTION_MANAGER) || hasRole(authentication, Role.ADMIN)) {
            receptions = receptionRepository.findAllByOrderByReceivedAtDesc();
        } else if (hasRole(authentication, Role.MILL_MANAGER)) {
            User user = currentUser(authentication);
            if (user.getAssignedMillId() == null) {
                return List.of();
            }
            receptions = receptionRepository.findByMillIdOrderByReceivedAtDesc(user.getAssignedMillId());
        } else if (hasRole(authentication, Role.FARMER)) {
            receptions = receptionRepository.findByFarmerIdOrderByReceivedAtDesc(authentication.getName());
        } else {
            throw new AccessDeniedException("Reception access requires a supported role");
        }
        return receptions.stream().map(MillReceptionDto::from).toList();
    }

    @Transactional(readOnly = true)
    public MillReceptionDto get(String id, Authentication authentication) {
        MillReception reception = receptionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Reception not found"));
        if (hasRole(authentication, Role.COLLECTION_MANAGER) || hasRole(authentication, Role.ADMIN)) {
            return MillReceptionDto.from(reception);
        }
        if (hasRole(authentication, Role.MILL_MANAGER)) {
            User user = currentUser(authentication);
            if (Objects.equals(user.getAssignedMillId(), reception.getMillId())) {
                return MillReceptionDto.from(reception);
            }
        }
        if (hasRole(authentication, Role.FARMER)
                && Objects.equals(authentication.getName(), reception.getFarmerId())) {
            return MillReceptionDto.from(reception);
        }
        throw new NotFoundException("Reception not found");
    }

    void validateWeights(double grossWeightKg, double tareKg, double binCapacityKg) {
        if (!Double.isFinite(grossWeightKg) || !Double.isFinite(tareKg)
                || !Double.isFinite(binCapacityKg) || grossWeightKg <= 0 || tareKg < 0 || binCapacityKg <= 0) {
            throw new BusinessRuleException("INVALID_WEIGHING", "Check weighing values");
        }
        double netWeightKg = grossWeightKg - tareKg;
        double fillRate = netWeightKg / binCapacityKg;
        if (netWeightKg <= 0 || !Double.isFinite(fillRate) || fillRate > MAX_BIN_FILL_RATE) {
            throw new BusinessRuleException("INVALID_WEIGHING", "Check weighing values");
        }
    }

    private MillReception newReception(String millId, MillReceptionCreateDto dto, Bin bin,
                                       HarvestRequest request, Instant receivedAt, String receivedById) {
        MillReception reception = new MillReception();
        reception.setMillId(millId);
        reception.setRoundId(dto.roundId());
        reception.setBinId(dto.binId());
        reception.setOrchardId(dto.orchardId());
        reception.setFarmerId(request.getFarmerId());
        reception.setHarvestRequestId(request.getId());
        reception.setGrossWeightKg(dto.grossWeightKg());
        reception.setTareKg(dto.tareKg());
        reception.setBinCapacityKg(bin.getCapacityKg());
        reception.computeNetAndFillRate();
        reception.setQuality(dto.quality().toEntity());
        reception.setSeason(request.getSeason());
        if (request.getOrchard() != null) {
            reception.setVarietyCode(request.getOrchard().getMainVarietyCode());
            reception.setDelegation(request.getOrchard().getDelegation());
            reception.setOrchardAreaHa(request.getOrchard().getAreaHa());
        }
        reception.setRequestValidatedAt(request.getValidatedAt());
        reception.setLeadTimeHours(leadTimeHours(request.getValidatedAt(), receivedAt));
        reception.setReceivedAt(receivedAt);
        reception.setReceivedById(receivedById);
        return reception;
    }

    private double leadTimeHours(Instant validatedAt, Instant receivedAt) {
        return validatedAt == null ? 0 : Math.max(0,
                Duration.between(validatedAt, receivedAt).toMillis() / 3_600_000.0);
    }

    private List<RoundBinDto> binsFor(CollectionRound round) {
        return equipmentRepository.findAllById(round.getEquipmentIds()).stream()
                .filter(Bin.class::isInstance)
                .map(Bin.class::cast)
                .map(RoundBinDto::from)
                .toList();
    }

    private void requireMillManagerAccess(String millId, Authentication authentication) {
        if (!hasRole(authentication, Role.MILL_MANAGER)) {
            throw new AccessDeniedException("Mill reception access requires a mill manager");
        }
        requireMillAccess(millId, authentication);
    }

    private void requireMillAccess(String millId, Authentication authentication) {
        if (hasRole(authentication, Role.ADMIN)) {
            return;
        }
        User user = currentUser(authentication);
        if (!hasRole(authentication, Role.MILL_MANAGER)
                || !Objects.equals(user.getAssignedMillId(), millId)) {
            throw new AccessDeniedException("The mill belongs to another manager");
        }
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findById(authentication.getName())
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private Mill findMill(String millId) {
        return millRepository.findById(millId).orElseThrow(() -> new NotFoundException("Mill not found"));
    }

    private boolean hasRole(Authentication authentication, Role role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.name()));
    }
}
