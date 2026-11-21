package tn.oliveconnect.round;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The fields a team leader can change while visiting one stop. */
public record RoundStopUpdateDto(
        @NotNull StopStatus status,
        Double harvestedKg,
        Integer crateCount,
        @Size(max = 500) String comment,
        Long version) {
}
