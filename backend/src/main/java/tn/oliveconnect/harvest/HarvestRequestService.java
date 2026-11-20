package tn.oliveconnect.harvest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tn.oliveconnect.common.ConflictException;
import tn.oliveconnect.common.FieldValidationException;
import tn.oliveconnect.common.NotFoundException;
import tn.oliveconnect.notification.NotificationService;
import tn.oliveconnect.notification.NotificationType;
import tn.oliveconnect.orchard.GeoPoint;
import tn.oliveconnect.orchard.Orchard;
import tn.oliveconnect.orchard.OrchardRepository;
import tn.oliveconnect.user.Role;

@Service
public class HarvestRequestService {

    private static final Set<HarvestRequestStatus> ACTIVE_STATUSES = Set.of(
            HarvestRequestStatus.SUBMITTED,
            HarvestRequestStatus.VALIDATED,
            HarvestRequestStatus.SCHEDULED,
            HarvestRequestStatus.IN_PROGRESS);

    private final HarvestRequestRepository harvestRequestRepository;
    private final OrchardRepository orchardRepository;
    private final NotificationService notificationService;
    private final PriorityCalculator priorityCalculator;

    public HarvestRequestService(HarvestRequestRepository harvestRequestRepository, OrchardRepository orchardRepository,
                                 NotificationService notificationService, PriorityCalculator priorityCalculator) {
        this.harvestRequestRepository = harvestRequestRepository;
        this.orchardRepository = orchardRepository;
        this.notificationService = notificationService;
        this.priorityCalculator = priorityCalculator;
    }

    public HarvestRequestDto create(HarvestRequestCreateDto dto, Authentication authentication) {
        String farmerId = authentication.getName();
        Orchard orchard = orchardRepository.findByIdAndOwnerId(dto.orchardId(), farmerId)
                .orElseThrow(() -> new NotFoundException("Orchard not found"));
        if (harvestRequestRepository.existsByOrchardIdAndStatusIn(dto.orchardId(), ACTIVE_STATUSES)) {
            throw new ConflictException("ACTIVE_REQUEST_EXISTS", "A request is already open for this orchard");
        }
        validateDateWindow(dto.desiredFrom(), dto.desiredTo());

        HarvestRequest request = new HarvestRequest();
        request.setFarmerId(farmerId);
        request.setOrchardId(orchard.getId());
        request.setOrchard(snapshot(orchard));
        request.setType(dto.type());
        request.setDesiredFrom(dto.desiredFrom());
        request.setDesiredTo(dto.desiredTo());
        request.setEstimatedQuantityKg(dto.estimatedQuantityKg());
        request.setComment(dto.comment());
        request.setSeason(seasonFor(dto.desiredFrom()));
        request.setPriorityScore(priorityCalculator.calculate(dto.type(), orchard.getCurrentMaturityIndex(),
                dto.desiredFrom()));
        request.transitionTo(HarvestRequestStatus.SUBMITTED, farmerId, null);

        HarvestRequest saved = harvestRequestRepository.save(request);
        notificationService.notifyCollectionManagers(NotificationType.NEW_REQUEST, "New harvest request",
                "A farmer submitted a new harvest request for " + orchard.getName(), linkFor(saved));
        return toDto(saved, authentication);
    }

    public List<HarvestRequestDto> list(Authentication authentication, HarvestRequestStatus status,
                                        HarvestType type, Boolean mine) {
        boolean manager = hasRole(authentication, Role.COLLECTION_MANAGER);
        boolean own = !manager || Boolean.TRUE.equals(mine);
        List<HarvestRequest> requests;
        if (own) {
            requests = harvestRequestRepository.findByFarmerIdOrderByCreatedAtDesc(authentication.getName());
        } else if (status == null) {
            requests = harvestRequestRepository.findAllByOrderByPriorityScoreDescCreatedAtAsc();
        } else {
            requests = harvestRequestRepository.findByStatusOrderByPriorityScoreDescCreatedAtAsc(status);
        }
        return requests.stream()
                .filter(request -> status == null || request.getStatus() == status)
                .filter(request -> type == null || request.getType() == type)
                .map(request -> toDto(request, authentication))
                .toList();
    }

    public HarvestRequestDto get(String id, Authentication authentication) {
        return toDto(findVisible(id, authentication), authentication);
    }

    public HarvestRequestDto update(String id, HarvestRequestUpdateDto dto, Authentication authentication) {
        HarvestRequest request = findOwned(id, authentication.getName());
        if (request.getStatus() != HarvestRequestStatus.SUBMITTED
                && request.getStatus() != HarvestRequestStatus.VALIDATED) {
            throw invalidTransition(request, HarvestRequestStatus.VALIDATED);
        }
        assertVersion(request, dto.version());

        Orchard orchard = orchardForUpdate(request, dto.orchardId(), authentication.getName());
        HarvestType type = dto.type() == null ? request.getType() : dto.type();
        LocalDate desiredFrom = dto.desiredFrom() == null ? request.getDesiredFrom() : dto.desiredFrom();
        LocalDate desiredTo = dto.desiredTo() == null ? request.getDesiredTo() : dto.desiredTo();
        double estimatedQuantityKg = dto.estimatedQuantityKg() == null
                ? request.getEstimatedQuantityKg() : dto.estimatedQuantityKg();
        validateDateWindow(desiredFrom, desiredTo);
        if (type == null) {
            throw new FieldValidationException("type", "must be provided");
        }
        if (!Double.isFinite(estimatedQuantityKg) || estimatedQuantityKg <= 0) {
            throw new FieldValidationException("estimatedQuantityKg", "must be greater than 0");
        }

        request.setOrchardId(orchard.getId());
        request.setOrchard(snapshot(orchard));
        request.setType(type);
        request.setDesiredFrom(desiredFrom);
        request.setDesiredTo(desiredTo);
        request.setEstimatedQuantityKg(estimatedQuantityKg);
        if (dto.comment() != null) {
            request.setComment(dto.comment());
        }
        request.setSeason(seasonFor(desiredFrom));
        request.setPriorityScore(priorityCalculator.calculate(type, orchard.getCurrentMaturityIndex(), desiredFrom));
        return toDto(harvestRequestRepository.save(request), authentication);
    }

    public HarvestRequestDto cancel(String id, HarvestRequestCancelDto dto, Authentication authentication) {
        HarvestRequest request = findOwned(id, authentication.getName());
        Long version = dto == null ? null : dto.version();
        String reason = dto == null ? null : dto.reason();
        assertVersion(request, version);
        if (!request.canTransitionTo(HarvestRequestStatus.CANCELLED)
                || (request.getStatus() != HarvestRequestStatus.SUBMITTED
                && request.getStatus() != HarvestRequestStatus.VALIDATED)) {
            throw invalidTransition(request, HarvestRequestStatus.CANCELLED);
        }
        request.transitionTo(HarvestRequestStatus.CANCELLED, authentication.getName(), reason);
        return toDto(harvestRequestRepository.save(request), authentication);
    }

    public HarvestRequestDto validate(String id, HarvestRequestVersionDto dto, Authentication authentication) {
        HarvestRequest request = findVisible(id, authentication);
        assertVersion(request, dto == null ? null : dto.version());
        if (!request.canTransitionTo(HarvestRequestStatus.VALIDATED)
                || request.getStatus() != HarvestRequestStatus.SUBMITTED) {
            throw invalidTransition(request, HarvestRequestStatus.VALIDATED);
        }
        request.transitionTo(HarvestRequestStatus.VALIDATED, authentication.getName(), null);
        request.setValidatedAt(Instant.now());
        HarvestRequest saved = harvestRequestRepository.save(request);
        notificationService.notifyUser(saved.getFarmerId(), NotificationType.REQUEST_VALIDATED,
                "Harvest request validated", "Your harvest request was validated", linkFor(saved));
        return toDto(saved, authentication);
    }

    public HarvestRequestDto reject(String id, HarvestRequestRejectDto dto, Authentication authentication) {
        if (dto == null || dto.reason() == null || dto.reason().isBlank()) {
            throw new FieldValidationException("reason", "must not be blank");
        }
        HarvestRequest request = findVisible(id, authentication);
        assertVersion(request, dto.version());
        if (!request.canTransitionTo(HarvestRequestStatus.REJECTED)
                || request.getStatus() != HarvestRequestStatus.SUBMITTED) {
            throw invalidTransition(request, HarvestRequestStatus.REJECTED);
        }
        request.setRejectionReason(dto.reason());
        request.transitionTo(HarvestRequestStatus.REJECTED, authentication.getName(), dto.reason());
        HarvestRequest saved = harvestRequestRepository.save(request);
        notificationService.notifyUser(saved.getFarmerId(), NotificationType.REQUEST_REJECTED,
                "Harvest request rejected", dto.reason(), linkFor(saved));
        return toDto(saved, authentication);
    }

    private HarvestRequest findVisible(String id, Authentication authentication) {
        if (hasRole(authentication, Role.COLLECTION_MANAGER)) {
            return harvestRequestRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Harvest request not found"));
        }
        return findOwned(id, authentication.getName());
    }

    private HarvestRequest findOwned(String id, String farmerId) {
        return harvestRequestRepository.findByIdAndFarmerId(id, farmerId)
                .orElseThrow(() -> new NotFoundException("Harvest request not found"));
    }

    private Orchard orchardForUpdate(HarvestRequest request, String orchardId, String farmerId) {
        if (orchardId == null || orchardId.equals(request.getOrchardId())) {
            return orchardRepository.findByIdAndOwnerId(request.getOrchardId(), farmerId)
                    .orElseThrow(() -> new NotFoundException("Orchard not found"));
        }
        Orchard orchard = orchardRepository.findByIdAndOwnerId(orchardId, farmerId)
                .orElseThrow(() -> new NotFoundException("Orchard not found"));
        if (harvestRequestRepository.existsByOrchardIdAndStatusIn(orchardId, ACTIVE_STATUSES)) {
            throw new ConflictException("ACTIVE_REQUEST_EXISTS", "A request is already open for this orchard");
        }
        return orchard;
    }

    private void assertVersion(HarvestRequest request, Long expectedVersion) {
        if (expectedVersion != null && !Objects.equals(expectedVersion, request.getVersion())) {
            throw new ConflictException("STALE_VERSION", "This record was changed by someone else, reload it");
        }
    }

    private ConflictException invalidTransition(HarvestRequest request, HarvestRequestStatus target) {
        String current = request.getStatus() == null ? "<new>" : request.getStatus().name();
        return new ConflictException("INVALID_TRANSITION",
                "Cannot transition harvest request from " + current + " to " + target.name());
    }

    private void validateDateWindow(LocalDate desiredFrom, LocalDate desiredTo) {
        LocalDate today = LocalDate.now();
        if (desiredFrom == null) {
            throw new FieldValidationException("desiredFrom", "must be provided");
        }
        if (desiredTo == null) {
            throw new FieldValidationException("desiredTo", "must be provided");
        }
        if (desiredFrom.isBefore(today)) {
            throw new FieldValidationException("desiredFrom", "must be today or in the future");
        }
        if (desiredTo.isBefore(today)) {
            throw new FieldValidationException("desiredTo", "must be today or in the future");
        }
        if (desiredTo.isBefore(desiredFrom)) {
            throw new FieldValidationException("desiredTo", "must be on or after desiredFrom");
        }
    }

    private OrchardSnapshot snapshot(Orchard orchard) {
        GeoPoint location = orchard.getLocation();
        GeoPoint copiedLocation = location == null ? null
                : new GeoPoint(location.longitude(), location.latitude());
        return new OrchardSnapshot(orchard.getId(), orchard.getName(), copiedLocation, orchard.getDelegation(),
                orchard.getMainVarietyCode(), orchard.getAreaHa(), orchard.getLastSeasonYieldKgHa(),
                orchard.getCurrentMaturityIndex());
    }

    private HarvestRequestDto toDto(HarvestRequest request, Authentication authentication) {
        return HarvestRequestDto.from(request, allowedActions(request, authentication));
    }

    private List<String> allowedActions(HarvestRequest request, Authentication authentication) {
        List<String> actions = new java.util.ArrayList<>();
        boolean owner = request.getFarmerId().equals(authentication.getName());
        if (owner && hasRole(authentication, Role.FARMER)
                && (request.getStatus() == HarvestRequestStatus.SUBMITTED
                || request.getStatus() == HarvestRequestStatus.VALIDATED)) {
            actions.add("MODIFY");
            actions.add("CANCEL");
        }
        if (hasRole(authentication, Role.COLLECTION_MANAGER)
                && request.getStatus() == HarvestRequestStatus.SUBMITTED) {
            actions.add("VALIDATE");
            actions.add("REJECT");
        }
        return List.copyOf(actions);
    }

    private String seasonFor(LocalDate date) {
        int startYear = date.getMonthValue() >= 7 ? date.getYear() : date.getYear() - 1;
        return startYear + "-" + (startYear + 1);
    }

    private String linkFor(HarvestRequest request) {
        return "/harvest-requests/" + request.getId();
    }

    private boolean hasRole(Authentication authentication, Role role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.name()));
    }
}
