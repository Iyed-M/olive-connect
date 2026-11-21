package tn.oliveconnect.mill;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MillReceptionCreateDto(
        @NotBlank String roundId,
        @NotBlank String binId,
        @NotBlank String orchardId,
        double grossWeightKg,
        double tareKg,
        @NotNull @Valid QualityAssessmentDto quality) {
}
