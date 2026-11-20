package tn.oliveconnect.harvest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.oliveconnect.common.ConflictException;
import tn.oliveconnect.common.FieldValidationException;
import tn.oliveconnect.common.NotFoundException;
import tn.oliveconnect.common.PageResponse;
import tn.oliveconnect.notification.NotificationService;
import tn.oliveconnect.notification.NotificationType;
import tn.oliveconnect.orchard.GeoPoint;
import tn.oliveconnect.orchard.OliveVariety;
import tn.oliveconnect.orchard.OliveVarietyRepository;
import tn.oliveconnect.orchard.Orchard;
import tn.oliveconnect.orchard.OrchardRepository;
import tn.oliveconnect.resource.BookingService;
import tn.oliveconnect.round.CollectionRound;
import tn.oliveconnect.round.CollectionRoundRepository;
import tn.oliveconnect.round.RoundStatus;
import tn.oliveconnect.round.RoundStop;
import tn.oliveconnect.user.Role;

@Service
@Transactional
public class HarvestRequestService {

    private static final Set<HarvestRequestStatus> ACTIVE_STATUSES = Set.of(
            HarvestRequestStatus.SUBMITTED,
            HarvestRequestStatus.VALIDATED,
            HarvestRequestStatus.SCHEDULED,
            HarvestRequestStatus.IN_PROGRESS);

    private final HarvestRequestRepository harvestRequestRepository;
    private final OrchardRepository orchardRepository;
    private final OliveVarietyRepository oliveVarietyRepository;
    private final MongoTemplate mongoTemplate;
    private final NotificationService notificationService;
    private final PriorityCalculator priorityCalculator;
    private final CollectionRoundRepository collectionRoundRepository;
    private final BookingService bookingService;

    public HarvestRequestService(HarvestRequestRepository harvestRequestRepository, OrchardRepository orchardRepository,
                                 OliveVarietyRepository oliveVarietyRepository, MongoTemplate mongoTemplate,
                                 NotificationService notificationService, PriorityCalculator priorityCalculator,
                                 CollectionRoundRepository collectionRoundRepository, BookingService bookingService) {
        this.harvestRequestRepository = harvestRequestRepository;
        this.orchardRepository = orchardRepository;
        this.oliveVarietyRepository = oliveVarietyRepository;
        this.mongoTemplate = mongoTemplate;
        this.notificationService = notificationService;
        this.priorityCalculator = priorityCalculator;
        this.collectionRoundRepository = collectionRoundRepository;
        this.bookingService = bookingService;
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
        request.setPriorityScore(priorityFor(dto.type(), orchard, null));
        request.transitionTo(HarvestRequestStatus.SUBMITTED, farmerId, null);

        HarvestRequest saved = harvestRequestRepository.save(request);
        notificationService.notifyCollectionManagers(NotificationType.NEW_REQUEST, "Nouvelle demande de récolte",
                "Un agriculteur a soumis une demande de récolte pour " + orchard.getName(), linkFor(saved));
        return toDto(saved, authentication);
    }

    @Transactional(readOnly = true)
    public PageResponse<HarvestRequestDto> list(Authentication authentication, HarvestRequestStatus status,
                                                HarvestType type, Boolean mine, int page, int size) {
        boolean manager = hasRole(authentication, Role.COLLECTION_MANAGER);
        boolean own = !manager || Boolean.TRUE.equals(mine);
        Sort sort = own
                ? Sort.by(Sort.Direction.DESC, "createdAt")
                : Sort.by(Sort.Direction.DESC, "priorityScore")
                        .and(Sort.by(Sort.Direction.ASC, "createdAt"));
        PageRequest pageable = PageRequest.of(page, size, sort);
        Query filter = new Query();
        if (own) {
            filter.addCriteria(Criteria.where("farmerId").is(authentication.getName()));
        }
        if (status != null) {
            filter.addCriteria(Criteria.where("status").is(status));
        }
        if (type != null) {
            filter.addCriteria(Criteria.where("type").is(type));
        }
        List<HarvestRequest> requests = mongoTemplate.find(Query.of(filter).with(pageable), HarvestRequest.class);
        Page<HarvestRequest> result = PageableExecutionUtils.getPage(requests, pageable,
                () -> mongoTemplate.count(Query.of(filter).limit(-1).skip(-1), HarvestRequest.class));
        return PageResponse.of(result, request -> toDto(request, authentication));
    }

    @Transactional(readOnly = true)
    public HarvestRequestDto get(String id, Authentication authentication) {
        return toDto(findVisible(id, authentication), authentication);
    }

    public HarvestRequestDto update(String id, HarvestRequestUpdateDto dto, Authentication authentication) {
        HarvestRequest request = findOwned(id, authentication.getName());
        boolean scheduled = request.getStatus() == HarvestRequestStatus.SCHEDULED;
        if (request.getStatus() != HarvestRequestStatus.SUBMITTED
                && request.getStatus() != HarvestRequestStatus.VALIDATED && !scheduled) {
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

        if (scheduled) {
            CollectionRound round = collectionRoundRepository.findById(request.getRoundId())
                    .orElseThrow(() -> new NotFoundException("Collection round not found"));
            if (round.getStatus() != RoundStatus.PLANNED) {
                throw new ConflictException("ROUND_STARTED", "Round already started — contact the collection manager");
            }
            boolean datesChanged = !Objects.equals(desiredFrom, request.getDesiredFrom())
                    || !Objects.equals(desiredTo, request.getDesiredTo())
                    || !Objects.equals(orchard.getId(), request.getOrchardId());
            if (datesChanged) {
                detachFromPlannedRound(request, round, authentication.getName(), "Request modified");
            } else if (!Objects.equals(estimatedQuantityKg, request.getEstimatedQuantityKg())) {
                round.getStops().stream()
                        .filter(stop -> request.getId().equals(stop.getHarvestRequestId()))
                        .findFirst().ifPresent(stop -> stop.setEstimatedKg(estimatedQuantityKg));
                round.setEstimatedTotalKg(round.getStops().stream().mapToDouble(RoundStop::getEstimatedKg).sum());
                collectionRoundRepository.save(round);
            }
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
        request.setPriorityScore(priorityFor(type, orchard, request.getCreatedAt()));
        return toDto(harvestRequestRepository.save(request), authentication);
    }

    public HarvestRequestDto cancel(String id, HarvestRequestCancelDto dto, Authentication authentication) {
        HarvestRequest request = findOwned(id, authentication.getName());
        Long version = dto == null ? null : dto.version();
        String reason = dto == null ? null : dto.reason();
        assertVersion(request, version);
        if (!request.canTransitionTo(HarvestRequestStatus.CANCELLED)) {
            throw invalidTransition(request, HarvestRequestStatus.CANCELLED);
        }
        if (request.getStatus() == HarvestRequestStatus.SCHEDULED) {
            CollectionRound round = collectionRoundRepository.findById(request.getRoundId())
                    .orElseThrow(() -> new NotFoundException("Collection round not found"));
            if (round.getStatus() != RoundStatus.PLANNED) {
                throw new ConflictException("ROUND_STARTED", "Round already started — contact the collection manager");
            }
            detachFromPlannedRound(request, round, authentication.getName(), reason);
            request.setRoundId(null);
            HarvestRequestDto cancelled = toDto(harvestRequestRepository.save(
                    transitionToCancelled(request, authentication.getName(), reason)), authentication);
            notificationService.notifyCollectionManagers(NotificationType.ROUND_UPDATED,
                    "Scheduled request cancelled", "A farmer cancelled a scheduled harvest request", linkFor(request));
            notificationService.notifyUser(round.getTeamLeaderId(), NotificationType.ROUND_UPDATED,
                    "Scheduled request cancelled", "A farmer cancelled a stop on " + round.getCode(),
                    "/rounds/" + round.getId());
            return cancelled;
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
                "Demande validée", "Votre demande de récolte a été validée", linkFor(saved));
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
                "Demande refusée", dto.reason(), linkFor(saved));
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
        boolean scheduledRoundIsPlanned = request.getStatus() != HarvestRequestStatus.SCHEDULED
                || collectionRoundRepository.findById(request.getRoundId())
                .map(round -> round.getStatus() == RoundStatus.PLANNED).orElse(false);
        if (owner && hasRole(authentication, Role.FARMER)
                && (request.getStatus() == HarvestRequestStatus.SUBMITTED
                || request.getStatus() == HarvestRequestStatus.VALIDATED
                || request.getStatus() == HarvestRequestStatus.SCHEDULED) && scheduledRoundIsPlanned) {
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

    private double priorityFor(HarvestType type, Orchard orchard, Instant createdAt) {
        OliveVariety variety = orchard.getMainVarietyCode() == null ? null
                : oliveVarietyRepository.findById(orchard.getMainVarietyCode()).orElse(null);
        double optimalMin = variety == null ? 0.0 : variety.getOptimalMaturityMin();
        double optimalMax = variety == null ? 0.0 : variety.getOptimalMaturityMax();
        long waitingDays = createdAt == null ? 0
                : Math.max(0, ChronoUnit.DAYS.between(createdAt.atZone(ZoneOffset.UTC).toLocalDate(),
                        LocalDate.now(ZoneOffset.UTC)));
        // M3 has no FieldAlert aggregate yet, so the alert term is deliberately false until M8.
        return priorityCalculator.calculate(type, orchard.getCurrentMaturityIndex(), optimalMin, optimalMax,
                waitingDays, false);
    }

    private boolean hasRole(Authentication authentication, Role role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.name()));
    }

    private HarvestRequest transitionToCancelled(HarvestRequest request, String userId, String reason) {
        request.transitionTo(HarvestRequestStatus.CANCELLED, userId, reason);
        return request;
    }

    private void detachFromPlannedRound(HarvestRequest request, CollectionRound round, String userId, String comment) {
        round.removeStopForRequest(request.getId());
        if (round.getStops().isEmpty()) {
            bookingService.releaseByRoundId(round.getId());
            round.transitionTo(RoundStatus.CANCELLED, userId, comment);
        }
        collectionRoundRepository.save(round);
        request.transitionTo(HarvestRequestStatus.VALIDATED, userId, comment);
        request.setRoundId(null);
    }
}
