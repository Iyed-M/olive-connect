package tn.oliveconnect.mill;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record MillCapacityDto(@NotNull @DecimalMin("0.0") Double availableCapacityKg) {

    public static MillCapacityDto from(Mill mill) {
        return new MillCapacityDto(mill.getAvailableCapacityKg());
    }
}
