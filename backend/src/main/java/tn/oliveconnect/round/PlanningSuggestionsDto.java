package tn.oliveconnect.round;

import java.time.LocalDate;
import java.util.List;
import tn.oliveconnect.harvest.HarvestRequestDto;
import tn.oliveconnect.mill.MillDto;
import tn.oliveconnect.resource.EquipmentDto;
import tn.oliveconnect.resource.WorkerDto;

public record PlanningSuggestionsDto(
        LocalDate date,
        List<HarvestRequestDto> requests,
        List<WorkerDto> availableWorkers,
        List<EquipmentDto> availableEquipment,
        List<MillDto> mills,
        List<PlanningGroupDto> groups,
        MillDto suggestedMill) {
}
