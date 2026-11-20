package tn.oliveconnect.harvest;

import jakarta.validation.constraints.Size;

public record HarvestRequestCancelDto(@Size(max = 500) String reason, Long version) {

    public HarvestRequestCancelDto {
        reason = reason == null || reason.isBlank() ? null : reason.trim();
    }
}
