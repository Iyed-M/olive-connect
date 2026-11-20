package tn.oliveconnect.round;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tn.oliveconnect.harvest.HarvestRequest;
import tn.oliveconnect.harvest.HarvestRequestDto;
import tn.oliveconnect.harvest.HarvestRequestRepository;
import tn.oliveconnect.harvest.HarvestRequestStatus;
import tn.oliveconnect.mill.MillDto;
import tn.oliveconnect.mill.MillRepository;
import tn.oliveconnect.resource.EquipmentDto;
import tn.oliveconnect.resource.ResourceAvailabilityService;

@Service
public class PlanningService {

    private final HarvestRequestRepository requestRepository;
    private final ResourceAvailabilityService availabilityService;
    private final MillRepository millRepository;

    public PlanningService(HarvestRequestRepository requestRepository, ResourceAvailabilityService availabilityService,
                           MillRepository millRepository) {
        this.requestRepository = requestRepository;
        this.availabilityService = availabilityService;
        this.millRepository = millRepository;
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
        var availability = availabilityService.find(selectedDate.atStartOfDay().toInstant(ZoneOffset.UTC),
                selectedDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), null);
        List<MillDto> mills = millRepository.findAll().stream()
                .sorted(Comparator.comparingDouble(mill -> distanceFromRequests(mill, requests)))
                .map(MillDto::from).toList();
        List<HarvestRequest> groupedRequests = groupRequests(requests, availability.equipment());
        PlanningGroupDto group = new PlanningGroupDto(groupedRequests.stream()
                .map(request -> HarvestRequestDto.from(request, List.of())).toList(),
                groupedRequests.stream().mapToDouble(HarvestRequest::getEstimatedQuantityKg).sum());
        MillDto suggestedMill = mills.stream()
                .filter(mill -> mill.availableCapacityKg() >= group.estimatedTotalKg())
                .findFirst().orElse(mills.isEmpty() ? null : mills.get(0));
        return new PlanningSuggestionsDto(selectedDate, requestDtos, availability.workers(), availability.equipment(),
                mills, requests.isEmpty() ? List.of() : List.of(group), suggestedMill);
    }

    private List<HarvestRequest> groupRequests(List<HarvestRequest> requests,
                                                List<tn.oliveconnect.resource.EquipmentDto> equipment) {
        if (requests.isEmpty()) {
            return List.of();
        }
        double capacity = equipment.stream().filter(item -> item.type() == tn.oliveconnect.resource.EquipmentType.BIN)
                .filter(item -> item.capacityKg() != null).mapToDouble(item -> item.capacityKg()).sum();
        HarvestRequest seed = requests.get(0);
        List<HarvestRequest> grouped = new ArrayList<>();
        grouped.add(seed);
        double estimated = seed.getEstimatedQuantityKg();
        List<HarvestRequest> nearest = requests.stream().skip(1)
                .sorted(Comparator.comparingDouble(request -> distanceKm(seed, request)))
                .filter(request -> distanceKm(seed, request) <= 10)
                .toList();
        for (HarvestRequest request : nearest) {
            if (estimated >= capacity) {
                break;
            }
            grouped.add(request);
            estimated += request.getEstimatedQuantityKg();
        }
        return grouped;
    }

    private double distanceFromRequests(tn.oliveconnect.mill.Mill mill, List<HarvestRequest> requests) {
        if (requests.isEmpty() || mill.getLocation() == null || requests.get(0).getOrchard() == null
                || requests.get(0).getOrchard().getLocation() == null) {
            return 0;
        }
        return distanceKm(requests.get(0).getOrchard().getLocation().longitude(),
                requests.get(0).getOrchard().getLocation().latitude(),
                mill.getLocation().longitude(), mill.getLocation().latitude());
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
