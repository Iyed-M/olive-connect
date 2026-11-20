package tn.oliveconnect.round;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.oliveconnect.common.BusinessRuleException;
import tn.oliveconnect.common.ConflictException;
import tn.oliveconnect.common.NotFoundException;
import tn.oliveconnect.common.StatusChange;
import tn.oliveconnect.harvest.HarvestRequest;
import tn.oliveconnect.harvest.HarvestRequestRepository;
import tn.oliveconnect.harvest.HarvestRequestStatus;
import tn.oliveconnect.mill.Mill;
import tn.oliveconnect.mill.MillRepository;
import tn.oliveconnect.notification.NotificationService;
import tn.oliveconnect.notification.NotificationType;
import tn.oliveconnect.resource.BookingService;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;
import tn.oliveconnect.user.UserRepository;

@Service
@Transactional
public class CollectionRoundService {

    private final CollectionRoundRepository roundRepository;
    private final HarvestRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final MillRepository millRepository;
    private final BookingService bookingService;
    private final NotificationService notificationService;

    public CollectionRoundService(CollectionRoundRepository roundRepository, HarvestRequestRepository requestRepository,
                                  UserRepository userRepository, MillRepository millRepository, BookingService bookingService,
                                  NotificationService notificationService) {
        this.roundRepository = roundRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.millRepository = millRepository;
        this.bookingService = bookingService;
        this.notificationService = notificationService;
    }

    public CollectionRoundDto plan(CollectionRoundCreateDto dto, Authentication authentication) {
        validateWindow(dto.date(), dto.startTime(), dto.endTime());
        String plannedById = authentication.getName();
        requireUserWithRole(dto.teamLeaderId(), Role.TEAM_LEADER, "team leader");
        Mill mill = findMill(dto.millId());
        List<HarvestRequest> requests = findRequestsInOrder(dto.requestIds());
        assertValidated(requests);

        CollectionRound round = buildRound(dto.date(), dto.startTime(), dto.endTime(), plannedById,
                dto.teamLeaderId(), dto.millId(), dto.workerIds(), dto.equipmentIds(), requests);
        roundRepository.save(round);

        Instant start = toInstant(dto.date(), dto.startTime());
        Instant end = toInstant(dto.date(), dto.endTime());
        bookingService.checkAndBook(round.getId(), dto.workerIds(), dto.equipmentIds(), start, end);

        scheduleRequests(requests, round, plannedById);
        List<PlanningWarningDto> warnings = capacityWarnings(mill, round.getEstimatedTotalKg());
        notifyRoundScheduled(requests, round);
        return CollectionRoundDto.from(round, warnings);
    }

    @Transactional(readOnly = true)
    public List<CollectionRoundDto> list(LocalDate from, LocalDate to, RoundStatus status, String teamLeaderId,
                                         Authentication authentication) {
        List<CollectionRound> rounds = roundRepository.findAll(Sort.by(Sort.Direction.ASC, "date")
                .and(Sort.by(Sort.Direction.ASC, "startTime")));
        return rounds.stream()
                .filter(round -> from == null || !round.getDate().isBefore(from))
                .filter(round -> to == null || !round.getDate().isAfter(to))
                .filter(round -> status == null || round.getStatus() == status)
                .filter(round -> teamLeaderId == null || teamLeaderId.equals(round.getTeamLeaderId()))
                .filter(round -> canView(round, authentication))
                .map(round -> CollectionRoundDto.from(round, List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CollectionRoundDto get(String id, Authentication authentication) {
        CollectionRound round = findRound(id);
        if (!canView(round, authentication)) {
            throw new NotFoundException("Collection round not found");
        }
        return CollectionRoundDto.from(round, List.of());
    }

    public CollectionRoundDto update(String id, CollectionRoundUpdateDto dto, Authentication authentication) {
        CollectionRound round = findRound(id);
        if (round.getStatus() != RoundStatus.PLANNED) {
            throw new ConflictException("ROUND_NOT_PLANNED", "Only a PLANNED round can be edited");
        }
        assertVersion(round, dto.version());

        LocalDate date = dto.date() == null ? round.getDate() : dto.date();
        LocalTime startTime = dto.startTime() == null ? round.getStartTime() : dto.startTime();
        LocalTime endTime = dto.endTime() == null ? round.getEndTime() : dto.endTime();
        String teamLeaderId = dto.teamLeaderId() == null ? round.getTeamLeaderId() : dto.teamLeaderId();
        String millId = dto.millId() == null ? round.getMillId() : dto.millId();
        List<String> workerIds = dto.workerIds() == null ? round.getWorkerIds() : dto.workerIds();
        List<String> equipmentIds = dto.equipmentIds() == null ? round.getEquipmentIds() : dto.equipmentIds();
        List<String> requestIds = dto.requestIds() == null
                ? round.getStops().stream().map(RoundStop::getHarvestRequestId).toList() : dto.requestIds();

        validateWindow(date, startTime, endTime);
        requireUserWithRole(teamLeaderId, Role.TEAM_LEADER, "team leader");
        Mill mill = findMill(millId);
        List<HarvestRequest> requests = findRequestsInOrder(requestIds);
        assertValidatedOrAlreadyScheduled(requests, round.getId());
        detachRemovedRequests(round, requestIds, authentication.getName());

        round.setDate(date);
        round.setStartTime(startTime);
        round.setEndTime(endTime);
        round.setTeamLeaderId(teamLeaderId);
        round.setMillId(millId);
        round.setWorkerIds(workerIds);
        round.setEquipmentIds(equipmentIds);
        round.setStops(buildStops(requests, startTime));
        round.setEstimatedTotalKg(requests.stream().mapToDouble(HarvestRequest::getEstimatedQuantityKg).sum());
        round.setSeason(seasonFor(date));
        roundRepository.save(round);

        bookingService.releaseByRoundId(round.getId());
        bookingService.checkAndBook(round.getId(), workerIds, equipmentIds, toInstant(date, startTime),
                toInstant(date, endTime));
        scheduleRequests(requests, round, authentication.getName());
        notifyRoundUpdated(requests, round);
        return CollectionRoundDto.from(round, capacityWarnings(mill, round.getEstimatedTotalKg()));
    }

    public void cancel(String id, RoundCancelDto dto, Authentication authentication) {
        CollectionRound round = findRound(id);
        if (round.getStatus() != RoundStatus.PLANNED) {
            throw new ConflictException("ROUND_STARTED", "A round that has started cannot be cancelled");
        }
        if (dto != null) {
            assertVersion(round, dto.version());
        }
        for (RoundStop stop : round.getStops()) {
            requestRepository.findById(stop.getHarvestRequestId()).ifPresent(request -> {
                if (request.getStatus() == HarvestRequestStatus.SCHEDULED) {
                    request.transitionTo(HarvestRequestStatus.VALIDATED, authentication.getName(),
                            dto == null ? null : dto.reason());
                    request.setRoundId(null);
                    requestRepository.save(request);
                }
            });
        }
        bookingService.releaseByRoundId(round.getId());
        round.transitionTo(RoundStatus.CANCELLED, authentication.getName(), dto == null ? null : dto.reason());
        roundRepository.save(round);
        round.getStops().stream().map(RoundStop::getFarmerId).distinct()
                .forEach(farmerId -> notificationService.notifyUser(farmerId, NotificationType.ROUND_CANCELLED,
                        "Round cancelled", "Your collection round has been cancelled", "/rounds/" + round.getId()));
    }

    private CollectionRound buildRound(LocalDate date, LocalTime startTime, LocalTime endTime, String plannedById,
                                       String teamLeaderId, String millId, List<String> workerIds,
                                       List<String> equipmentIds, List<HarvestRequest> requests) {
        CollectionRound round = new CollectionRound();
        round.setCode("T-" + date.getYear() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        round.setDate(date);
        round.setStartTime(startTime);
        round.setEndTime(endTime);
        round.setStatus(RoundStatus.PLANNED);
        round.setPlannedById(plannedById);
        round.setTeamLeaderId(teamLeaderId);
        round.setMillId(millId);
        round.setWorkerIds(workerIds);
        round.setEquipmentIds(equipmentIds);
        round.setStops(buildStops(requests, startTime));
        round.setEstimatedTotalKg(requests.stream().mapToDouble(HarvestRequest::getEstimatedQuantityKg).sum());
        round.setSeason(seasonFor(date));
        round.getStatusHistory().add(new StatusChange(null, RoundStatus.PLANNED.name(), Instant.now(), plannedById, null));
        return round;
    }

    private List<RoundStop> buildStops(List<HarvestRequest> requests, LocalTime startTime) {
        List<RoundStop> stops = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            stops.add(RoundStop.from(requests.get(i), i + 1, startTime.plusMinutes(30L * i)));
        }
        return stops;
    }

    private List<HarvestRequest> findRequestsInOrder(List<String> ids) {
        Set<String> unique = new HashSet<>(ids);
        if (unique.size() != ids.size()) {
            throw new BusinessRuleException("DUPLICATE_REQUEST", "A harvest request was selected twice");
        }
        Map<String, HarvestRequest> byId = new HashMap<>();
        requestRepository.findAllById(ids).forEach(request -> byId.put(request.getId(), request));
        List<HarvestRequest> ordered = new ArrayList<>();
        for (String id : ids) {
            HarvestRequest request = byId.get(id);
            if (request == null) {
                throw new NotFoundException("Harvest request " + id + " not found");
            }
            ordered.add(request);
        }
        return ordered;
    }

    private void assertValidated(List<HarvestRequest> requests) {
        requests.stream().filter(request -> request.getStatus() != HarvestRequestStatus.VALIDATED).findFirst()
                .ifPresent(request -> {
                    throw new BusinessRuleException("REQUEST_NOT_VALIDATED",
                            "Request " + request.getId() + " is not validated");
                });
    }

    private void assertValidatedOrAlreadyScheduled(List<HarvestRequest> requests, String roundId) {
        requests.stream().filter(request -> request.getStatus() != HarvestRequestStatus.VALIDATED
                && !(request.getStatus() == HarvestRequestStatus.SCHEDULED && roundId.equals(request.getRoundId())))
                .findFirst().ifPresent(request -> {
                    throw new BusinessRuleException("REQUEST_NOT_VALIDATED",
                            "Request " + request.getId() + " is not validated");
                });
    }

    private void scheduleRequests(List<HarvestRequest> requests, CollectionRound round, String byUserId) {
        for (HarvestRequest request : requests) {
            if (request.getStatus() == HarvestRequestStatus.VALIDATED) {
                request.transitionTo(HarvestRequestStatus.SCHEDULED, byUserId, round.getCode());
            }
            request.setRoundId(round.getId());
            requestRepository.save(request);
        }
    }

    private void detachRemovedRequests(CollectionRound round, List<String> newRequestIds, String byUserId) {
        Set<String> selected = new HashSet<>(newRequestIds);
        for (RoundStop stop : round.getStops()) {
            if (selected.contains(stop.getHarvestRequestId())) {
                continue;
            }
            requestRepository.findById(stop.getHarvestRequestId()).ifPresent(request -> {
                if (request.getStatus() == HarvestRequestStatus.SCHEDULED
                        && round.getId().equals(request.getRoundId())) {
                    request.transitionTo(HarvestRequestStatus.VALIDATED, byUserId, "Removed from round");
                    request.setRoundId(null);
                    requestRepository.save(request);
                }
            });
        }
    }

    private List<PlanningWarningDto> capacityWarnings(Mill mill, double estimatedKg) {
        if (estimatedKg <= mill.getAvailableCapacityKg()) {
            return List.of();
        }
        return List.of(new PlanningWarningDto("MILL_CAPACITY_LOW",
                "The estimated load exceeds the mill's available capacity ("
                        + mill.getAvailableCapacityKg() + " kg)."));
    }

    private void notifyRoundScheduled(List<HarvestRequest> requests, CollectionRound round) {
        requests.stream().map(HarvestRequest::getFarmerId).distinct()
                .forEach(farmerId -> notificationService.notifyUser(farmerId, NotificationType.ROUND_SCHEDULED,
                        "Collection round scheduled", "Your harvest request was added to " + round.getCode(),
                        "/rounds/" + round.getId()));
        notificationService.notifyUser(round.getTeamLeaderId(), NotificationType.ROUND_ASSIGNED,
                "Collection round assigned", "You are assigned to " + round.getCode(), "/rounds/" + round.getId());
    }

    private void notifyRoundUpdated(List<HarvestRequest> requests, CollectionRound round) {
        requests.stream().map(HarvestRequest::getFarmerId).distinct()
                .forEach(farmerId -> notificationService.notifyUser(farmerId, NotificationType.ROUND_UPDATED,
                        "Collection round updated", "Your collection round was updated", "/rounds/" + round.getId()));
        notificationService.notifyUser(round.getTeamLeaderId(), NotificationType.ROUND_UPDATED,
                "Collection round updated", "Your assigned round was updated", "/rounds/" + round.getId());
    }

    private Mill findMill(String id) {
        return millRepository.findById(id).orElseThrow(() -> new NotFoundException("Mill not found"));
    }

    private CollectionRound findRound(String id) {
        return roundRepository.findById(id).orElseThrow(() -> new NotFoundException("Collection round not found"));
    }

    private void requireUserWithRole(String id, Role role, String label) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("INVALID_" + role.name(), label + " not found"));
        if (!user.getRoles().contains(role) || !user.isEnabled()) {
            throw new BusinessRuleException("INVALID_" + role.name(), "Selected " + label + " is not active");
        }
    }

    private boolean canView(CollectionRound round, Authentication authentication) {
        if (hasRole(authentication, Role.COLLECTION_MANAGER) || hasRole(authentication, Role.LOGISTICS_MANAGER)
                || hasRole(authentication, Role.ADMIN)) {
            return true;
        }
        if (hasRole(authentication, Role.TEAM_LEADER)) {
            return Objects.equals(round.getTeamLeaderId(), authentication.getName());
        }
        if (hasRole(authentication, Role.MILL_MANAGER)) {
            return userRepository.findById(authentication.getName())
                    .map(user -> Objects.equals(user.getAssignedMillId(), round.getMillId())).orElse(false);
        }
        return false;
    }

    private boolean hasRole(Authentication authentication, Role role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + role.name()).equals(authority.getAuthority()));
    }

    private void validateWindow(LocalDate date, LocalTime startTime, LocalTime endTime) {
        if (date == null || startTime == null || endTime == null) {
            throw new BusinessRuleException("INVALID_TIME_WINDOW", "Round date and start/end times are required");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BusinessRuleException("INVALID_TIME_WINDOW", "The round end time must be after its start time");
        }
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).toInstant(ZoneOffset.UTC);
    }

    private String seasonFor(LocalDate date) {
        int year = date.getMonthValue() >= 9 ? date.getYear() : date.getYear() - 1;
        return year + "-" + (year + 1);
    }

    private void assertVersion(CollectionRound round, Long expectedVersion) {
        if (expectedVersion != null && !Objects.equals(expectedVersion, round.getVersion())) {
            throw new ConflictException("STALE_VERSION", "This round was changed by someone else, reload it");
        }
    }
}
