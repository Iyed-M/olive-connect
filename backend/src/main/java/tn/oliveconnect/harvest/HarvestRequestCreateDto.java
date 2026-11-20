package tn.oliveconnect.harvest;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record HarvestRequestCreateDto(
        @NotBlank String orchardId,
        @NotNull HarvestType type,
        @NotNull @FutureOrPresent LocalDate desiredFrom,
        @NotNull @FutureOrPresent LocalDate desiredTo,
        @NotNull @Positive Double estimatedQuantityKg,
        @Size(max = 1000) String comment) {

    public HarvestRequestCreateDto {
        orchardId = orchardId == null ? null : orchardId.trim();
        comment = comment == null || comment.isBlank() ? null : comment.trim();
    }
}
