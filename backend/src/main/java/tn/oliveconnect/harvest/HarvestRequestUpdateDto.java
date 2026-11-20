package tn.oliveconnect.harvest;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record HarvestRequestUpdateDto(
        String orchardId,
        HarvestType type,
        LocalDate desiredFrom,
        LocalDate desiredTo,
        @Positive Double estimatedQuantityKg,
        @Size(max = 1000) String comment,
        Long version) {

    public HarvestRequestUpdateDto {
        orchardId = orchardId == null ? null : orchardId.trim();
        comment = comment == null || comment.isBlank() ? null : comment.trim();
    }
}
