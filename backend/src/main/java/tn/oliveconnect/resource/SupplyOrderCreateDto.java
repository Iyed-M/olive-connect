package tn.oliveconnect.resource;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record SupplyOrderCreateDto(
        @NotBlank String toolId,
        @Min(1) int quantity,
        @NotBlank @Size(max = 120) String supplier,
        LocalDate expectedAt) {
}
