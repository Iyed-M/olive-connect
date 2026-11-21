package tn.oliveconnect.resource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import tn.oliveconnect.common.NotFoundException;

/** Calendar of one worker or equipment: bookings of rounds plus unavailability periods (FR-3.3). */
@Service
public class ResourceCalendarService {

    private final WorkerRepository workerRepository;
    private final EquipmentRepository equipmentRepository;
    private final ResourceBookingRepository bookingRepository;

    public ResourceCalendarService(WorkerRepository workerRepository, EquipmentRepository equipmentRepository,
                                   ResourceBookingRepository bookingRepository) {
        this.workerRepository = workerRepository;
        this.equipmentRepository = equipmentRepository;
        this.bookingRepository = bookingRepository;
    }

    public ResourceCalendarDto find(String resourceId, Instant from, Instant to) {
        Optional<Worker> worker = workerRepository.findById(resourceId);
        ResourceKind kind;
        String label;
        List<Unavailability> periods;
        if (worker.isPresent()) {
            kind = ResourceKind.WORKER;
            label = worker.get().getFirstName() + " " + worker.get().getLastName();
            periods = worker.get().getUnavailabilities();
        } else {
            Equipment equipment = equipmentRepository.findById(resourceId)
                    .orElseThrow(() -> new NotFoundException("Resource not found"));
            kind = ResourceKind.EQUIPMENT;
            label = equipment.getCode();
            periods = equipment.getUnavailabilities();
        }
        List<ResourceCalendarDto.BookingItem> bookings = bookingRepository
                .findOverlapping(List.of(resourceId), from, to).stream()
                .sorted(java.util.Comparator.comparing(ResourceBooking::getStart))
                .map(b -> new ResourceCalendarDto.BookingItem(b.getId(), b.getRoundId(), b.getStart(), b.getEnd()))
                .toList();
        List<UnavailabilityDto> unavailabilities = periods.stream()
                .filter(p -> p.getFrom().isBefore(to) && p.getTo().isAfter(from))
                .map(UnavailabilityDto::from).toList();
        return new ResourceCalendarDto(resourceId, kind, label, bookings, unavailabilities);
    }
}
