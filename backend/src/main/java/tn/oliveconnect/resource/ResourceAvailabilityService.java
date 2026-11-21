package tn.oliveconnect.resource;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ResourceAvailabilityService {

    private final WorkerRepository workerRepository;
    private final EquipmentRepository equipmentRepository;
    private final ResourceBookingRepository bookingRepository;

    public ResourceAvailabilityService(WorkerRepository workerRepository, EquipmentRepository equipmentRepository,
                                       ResourceBookingRepository bookingRepository) {
        this.workerRepository = workerRepository;
        this.equipmentRepository = equipmentRepository;
        this.bookingRepository = bookingRepository;
    }

    public ResourceAvailabilityDto find(Instant start, Instant end, String type) {
        boolean workersRequested = type == null || type.isBlank() || "WORKER".equalsIgnoreCase(type);
        boolean equipmentRequested = !"WORKER".equalsIgnoreCase(type);
        List<ResourceAvailabilityItemDto> workers = workersRequested ? workerAvailability(start, end) : List.of();
        List<ResourceAvailabilityItemDto> equipment = equipmentRequested
                ? equipmentAvailability(start, end, type) : List.of();
        return new ResourceAvailabilityDto(workers, equipment);
    }

    public boolean isTeamLeaderAvailable(String id, Instant start, Instant end) {
        return bookingRepository.findOverlapping(List.of(id), start, end).isEmpty();
    }

    private List<ResourceAvailabilityItemDto> workerAvailability(Instant start, Instant end) {
        List<Worker> candidates = workerRepository.findAll();
        Set<String> booked = bookedIds(candidates.stream().map(Worker::getId).toList(), start, end);
        return candidates.stream().map(worker -> {
            Optional<String> unavailability = unavailabilityReason(worker.getUnavailabilities(), start, end);
            boolean available = worker.isActive() && unavailability.isEmpty() && !booked.contains(worker.getId());
            String reason = !worker.isActive() ? "Inactive" : unavailability.orElse(
                    booked.contains(worker.getId()) ? "Booked" : null);
            return new ResourceAvailabilityItemDto(worker.getId(), ResourceKind.WORKER, ResourceKind.WORKER.name(),
                    null, worker.getFirstName() + " " + worker.getLastName(), worker.getFirstName(),
                    worker.getLastName(), worker.isActive() ? "ACTIVE" : "INACTIVE", available, null, reason);
        }).toList();
    }

    private List<ResourceAvailabilityItemDto> equipmentAvailability(Instant start, Instant end, String type) {
        List<Equipment> candidates = equipmentRepository.findAll().stream()
                .filter(equipment -> type == null || type.isBlank() || "EQUIPMENT".equalsIgnoreCase(type)
                        || equipment.getType().name().equalsIgnoreCase(type))
                .toList();
        Set<String> booked = bookedIds(candidates.stream().map(Equipment::getId).toList(), start, end);
        return candidates.stream().map(equipment -> {
            Optional<String> unavailability = unavailabilityReason(equipment.getUnavailabilities(), start, end);
            boolean available = equipment.getStatus() == EquipmentStatus.AVAILABLE
                    && unavailability.isEmpty() && !booked.contains(equipment.getId());
            String reason = equipment.getStatus() != EquipmentStatus.AVAILABLE ? equipment.getStatus().name()
                    : unavailability.orElse(booked.contains(equipment.getId()) ? "Booked" : null);
            Double capacityKg = equipment instanceof Bin bin ? bin.getCapacityKg() : null;
            return new ResourceAvailabilityItemDto(equipment.getId(), ResourceKind.EQUIPMENT,
                    equipment.getType().name(), equipment.getCode(), equipment.getCode(), null, null,
                    equipment.getStatus().name(), available, capacityKg, reason);
        }).toList();
    }

    private Set<String> bookedIds(List<String> ids, Instant start, Instant end) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(bookingRepository.findOverlapping(ids, start, end).stream()
                .map(ResourceBooking::getResourceId).toList());
    }

    private Optional<String> unavailabilityReason(List<Unavailability> periods, Instant start, Instant end) {
        if (periods == null) {
            return Optional.empty();
        }
        return periods.stream()
                .filter(period -> period.getFrom().isBefore(end) && period.getTo().isAfter(start))
                .map(period -> period.getReason() == null || period.getReason().isBlank()
                        ? "Unavailable" : period.getReason())
                .findFirst();
    }
}
