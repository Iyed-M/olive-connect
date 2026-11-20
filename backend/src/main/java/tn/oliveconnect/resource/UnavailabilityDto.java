package tn.oliveconnect.resource;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Request and response shape of one unavailability period. */
public record UnavailabilityDto(@NotNull Instant from, @NotNull Instant to, String reason) {

    static UnavailabilityDto from(Unavailability u) {
        return new UnavailabilityDto(u.getFrom(), u.getTo(), u.getReason());
    }
}
