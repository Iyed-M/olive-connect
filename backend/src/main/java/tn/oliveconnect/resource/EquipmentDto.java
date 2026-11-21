package tn.oliveconnect.resource;

import java.time.Instant;
import java.util.List;
import tn.oliveconnect.common.Correction;

/** One shape for the whole hierarchy: the fields of the other subclasses are null (and sent as null). */
public record EquipmentDto(String id, EquipmentType type, String code, EquipmentStatus status,
                           String plateNumber, Integer powerHp,
                           String serialNumber, Double capacityKg,
                           ToolType toolType, Integer stockQuantity,
                           List<UnavailabilityDto> unavailabilities, List<Correction> corrections,
                           Instant createdAt, Long version) {

    static EquipmentDto from(Equipment e) {
        List<UnavailabilityDto> unavailabilities = e.getUnavailabilities().stream().map(UnavailabilityDto::from).toList();
        return switch (e) {
            case Tractor t -> new EquipmentDto(t.getId(), t.getType(), t.getCode(), t.getStatus(),
                    t.getPlateNumber(), t.getPowerHp(), null, null, null, null,
                    unavailabilities, List.copyOf(t.getCorrections()), t.getCreatedAt(), t.getVersion());
            case Bin b -> new EquipmentDto(b.getId(), b.getType(), b.getCode(), b.getStatus(),
                    null, null, b.getSerialNumber(), b.getCapacityKg(), null, null,
                    unavailabilities, List.copyOf(b.getCorrections()), b.getCreatedAt(), b.getVersion());
            case Tool t -> new EquipmentDto(t.getId(), t.getType(), t.getCode(), t.getStatus(),
                    null, null, null, null, t.getToolType(), t.getStockQuantity(),
                    unavailabilities, List.copyOf(t.getCorrections()), t.getCreatedAt(), t.getVersion());
            default -> throw new IllegalStateException("Unknown equipment class " + e.getClass());
        };
    }
}
