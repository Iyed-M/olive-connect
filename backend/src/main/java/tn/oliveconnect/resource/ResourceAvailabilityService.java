package tn.oliveconnect.resource;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
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
        List<Worker> workers = workersRequested ? freeWorkers(start, end) : List.of();
        List<Equipment> equipment = equipmentRequested ? freeEquipment(start, end, type) : List.of();
        return new ResourceAvailabilityDto(workers.stream().map(WorkerDto::from).toList(),
                equipment.stream().map(EquipmentDto::from).toList());
    }

    private List<Worker> freeWorkers(Instant start, Instant end) {
        List<Worker> candidates = workerRepository.findAll().stream().filter(Worker::isActive)
                .filter(worker -> available(worker.getUnavailabilities(), start, end)).toList();
        Set<String> booked = bookedIds(candidates.stream().map(Worker::getId).toList(), start, end);
        return candidates.stream().filter(worker -> !booked.contains(worker.getId())).toList();
    }

    private List<Equipment> freeEquipment(Instant start, Instant end, String type) {
        List<Equipment> candidates = equipmentRepository.findAll().stream()
                .filter(equipment -> equipment.getStatus() == EquipmentStatus.AVAILABLE)
                .filter(equipment -> type == null || type.isBlank() || "EQUIPMENT".equalsIgnoreCase(type)
                        || equipment.getType().name().equalsIgnoreCase(type))
                .filter(equipment -> available(equipment.getUnavailabilities(), start, end)).toList();
        Set<String> booked = bookedIds(candidates.stream().map(Equipment::getId).toList(), start, end);
        return candidates.stream().filter(equipment -> !booked.contains(equipment.getId())).toList();
    }

    private Set<String> bookedIds(List<String> ids, Instant start, Instant end) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(bookingRepository.findOverlapping(ids, start, end).stream()
                .map(ResourceBooking::getResourceId).toList());
    }

    private boolean available(List<Unavailability> periods, Instant start, Instant end) {
        return periods == null || periods.stream().noneMatch(period -> period.getFrom().isBefore(end)
                && period.getTo().isAfter(start));
    }
}
