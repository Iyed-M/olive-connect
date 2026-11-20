package tn.oliveconnect.round;

import jakarta.validation.constraints.Size;

public record RoundCancelDto(@Size(max = 500) String reason, Long version) {
}
