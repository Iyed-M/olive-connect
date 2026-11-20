package tn.oliveconnect.resource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Body of POST and PUT /equipment. "type" tells which fields are used: TRACTOR (plateNumber, powerHp),
 * BIN (serialNumber, capacityKg), NET and SHAKER (stockQuantity). The other fields are ignored.
 * "status" defaults to AVAILABLE when omitted.
 */
public record EquipmentRequestDto(
        @NotNull EquipmentType type,
        @NotBlank String code,
        EquipmentStatus status,
        String plateNumber,
        @Positive Integer powerHp,
        String serialNumber,
        @Positive Double capacityKg,
        @PositiveOrZero Integer stockQuantity) {

    public EquipmentRequestDto {
        code = code == null ? null : code.trim();
    }
}
