package tn.oliveconnect.resource;

import java.time.Instant;
import java.util.List;

/** Everything that occupies one resource between "from" and "to". */
public record ResourceCalendarDto(String resourceId, ResourceKind resourceKind, String label,
                                  List<BookingItem> bookings, List<UnavailabilityDto> unavailabilities) {

    /** An ACTIVE booking, created by a round (M5). */
    public record BookingItem(String id, String roundId, Instant start, Instant end) {
    }
}
