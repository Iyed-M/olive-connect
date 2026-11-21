package tn.oliveconnect.round;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Staff correction of the harvested weight of a COMPLETED round (FR-6.5). The comment is the justification. */
public record RoundCorrectionDto(
        @NotNull @DecimalMin("0") Double totalHarvestedKg,
        @NotBlank @Size(max = 500) String comment) {
}
