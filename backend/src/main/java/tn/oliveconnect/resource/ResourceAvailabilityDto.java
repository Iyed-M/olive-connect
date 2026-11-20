package tn.oliveconnect.resource;

import java.util.List;

public record ResourceAvailabilityDto(List<WorkerDto> workers, List<EquipmentDto> equipment) {
}
