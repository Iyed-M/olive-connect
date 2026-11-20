package tn.oliveconnect.harvest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HarvestRequestRejectDto(
        @NotBlank @Size(max = 500) String reason,
        Long version) {

    public HarvestRequestRejectDto {
        reason = reason == null ? null : reason.trim();
    }
}
