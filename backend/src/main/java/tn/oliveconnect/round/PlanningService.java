package tn.oliveconnect.round;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.NearQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tn.oliveconnect.harvest.HarvestRequest;
import tn.oliveconnect.harvest.HarvestRequestDto;
import tn.oliveconnect.harvest.HarvestRequestRepository;
import tn.oliveconnect.harvest.HarvestRequestStatus;
import tn.oliveconnect.mill.MillDto;
import tn.oliveconnect.mill.MillRepository;
import tn.oliveconnect.resource.ResourceAvailabilityService;
import tn.oliveconnect.resource.ResourceAvailabilityItemDto;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.UserRepository;

@Service
public class PlanningService {

    private final HarvestRequestRepository requestRepository;
    private final ResourceAvailabilityService availabilityService;
    private final MillRepository millRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    public PlanningService(HarvestRequestRepository requestRepository, ResourceAvailabilityService availabilityService,
                           MillRepository millRepository, UserRepository userRepository, MongoTemplate mongoTemplate) {
        this.requestRepository = requestRepository;
        this.availabilityService = availabilityService;
        this.millRepository = millRepository;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public PlanningSuggestionsDto suggestions(LocalDate date, Authentication authentication) {
        LocalDate selectedDate = date == null ? LocalDate.now(ZoneOffset.UTC).plusDays(1) : date;
        List<HarvestRequest> requests = requestRepository.findByStatusOrderByPriorityScoreDescCreatedAtAsc(
                        HarvestRequestStatus.VALIDATED).stream()
                .filter(request -> request.getDesiredFrom() == null || !selectedDate.isBefore(request.getDesiredFrom()))
                .filter(request -> request.getDesiredTo() == null || !selectedDate.isAfter(request.getDesiredTo()))
                .toList();
        List<HarvestRequestDto> requestDtos = requests.stream()
                .map(request -> HarvestRequestDto.from(request, List.of()))
                .toList();
        var slotStart = selectedDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        var slotEnd = selectedDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var availability = availabilityService.find(slotStart, slotEnd, null);
        List<MillDto> mills = nearestMills(requests).stream().map(MillDto::from).toList();
        List<HarvestRequest> groupedRequests = groupRequests(requests, availability.equipment());
        double estimatedTotalKg = groupedRequests.stream()
                .mapToDouble(HarvestRequest::getEstimatedQuantityKg).sum();
        MillDto suggestedMill = mills.stream()
                .filter(mill -> mill.availableCapacityKg() >= estimatedTotalKg)
                .findFirst().orElse(null);
        List<PlanningWarningDto> warnings = suggestedMill == null && !groupedRequests.isEmpty()
                ? List.of(new PlanningWarningDto("MILL_CAPACITY_LOW",
                        "No available mill has enough capacity for the suggested load.")) : List.of();
        PlanningGroupDto group = new PlanningGroupDto(groupedRequests.stream()
                .map(request -> HarvestRequestDto.from(request, List.of())).toList(),
                groupedRequests.stream().map(HarvestRequest::getId).toList(), estimatedTotalKg,
                suggestedMill == null ? null : suggestedMill.id());
        List<TeamLeaderOptionDto> teamLeaders = userRepository
                .findByRolesContainingAndEnabledTrueOrderByLastNameAscFirstNameAsc(Role.TEAM_LEADER).stream()
                .map(user -> TeamLeaderOptionDto.from(user,
                        availabilityService.isTeamLeaderAvailable(user.getId(), slotStart, slotEnd))).toList();
        return new PlanningSuggestionsDto(selectedDate, requestDtos, availability.workers(), availability.equipment(),
                teamLeaders, mills, requests.isEmpty() ? List.of() : List.of(group), suggestedMill, warnings);
    }

    private List<HarvestRequest> groupRequests(List<HarvestRequest> requests,
                                                List<ResourceAvailabilityItemDto> equipment) {
        if (requests.isEmpty()) {
            return List.of();
        }
        double capacity = equipment.stream().filter(ResourceAvailabilityItemDto::available)
                .filter(item -> "BIN".equals(item.resourceType()))
                .filter(item -> item.capacityKg() != null).mapToDouble(item -> item.capacityKg()).sum();
        HarvestRequest seed = requests.get(0);
        List<HarvestRequest> grouped = new ArrayList<>();
        grouped.add(seed);
        double estimated = seed.getEstimatedQuantityKg();
        List<HarvestRequest> nearest = nearestRequests(seed, requests.subList(1, requests.size()));
        for (HarvestRequest request : nearest) {
            if (estimated >= capacity) {
                break;
            }
            grouped.add(request);
            estimated += request.getEstimatedQuantityKg();
        }
        return grouped;
    }

    private List<HarvestRequest> nearestRequests(HarvestRequest seed, List<HarvestRequest> candidates) {
        if (candidates.isEmpty() || seed.getOrchard() == null || seed.getOrchard().getLocation() == null) {
            return candidates.stream().sorted(Comparator.comparingDouble(request -> distanceKm(seed, request))).toList();
        }
        Point origin = pointFor(seed.getOrchard().getLocation().longitude(), seed.getOrchard().getLocation().latitude());
        Query candidateFilter = Query.query(Criteria.where("_id")
                .in(candidates.stream().map(HarvestRequest::getId).toList()));
        GeoResults<HarvestRequest> results = mongoTemplate.geoNear(
                NearQuery.near(origin, Metrics.KILOMETERS).spherical(true).maxDistance(10, Metrics.KILOMETERS)
                        .query(candidateFilter), HarvestRequest.class);
        return results.getContent().stream().map(result -> result.getContent()).toList();
    }

    private List<tn.oliveconnect.mill.Mill> nearestMills(List<HarvestRequest> requests) {
        if (requests.isEmpty() || requests.get(0).getOrchard() == null
                || requests.get(0).getOrchard().getLocation() == null) {
            return millRepository.findAll().stream()
                    .sorted(Comparator.comparing(tn.oliveconnect.mill.Mill::getName)).toList();
        }
        var location = requests.get(0).getOrchard().getLocation();
        GeoResults<tn.oliveconnect.mill.Mill> results = mongoTemplate.geoNear(
                NearQuery.near(pointFor(location.longitude(), location.latitude()), Metrics.KILOMETERS).spherical(true),
                tn.oliveconnect.mill.Mill.class);
        return results.getContent().stream().map(result -> result.getContent()).toList();
    }

    private Point pointFor(double longitude, double latitude) {
        return new Point(longitude, latitude);
    }

    private double distanceKm(HarvestRequest left, HarvestRequest right) {
        if (left.getOrchard() == null || right.getOrchard() == null
                || left.getOrchard().getLocation() == null || right.getOrchard().getLocation() == null) {
            return Double.MAX_VALUE;
        }
        return distanceKm(left.getOrchard().getLocation().longitude(), left.getOrchard().getLocation().latitude(),
                right.getOrchard().getLocation().longitude(), right.getOrchard().getLocation().latitude());
    }

    private double distanceKm(double longitude1, double latitude1, double longitude2, double latitude2) {
        double lat1 = Math.toRadians(latitude1);
        double lat2 = Math.toRadians(latitude2);
        double latDelta = lat2 - lat1;
        double lonDelta = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(latDelta / 2) * Math.sin(latDelta / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(lonDelta / 2) * Math.sin(lonDelta / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
