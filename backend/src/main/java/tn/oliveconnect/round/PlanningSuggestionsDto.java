package tn.oliveconnect.round;

import java.time.LocalDate;
import java.util.List;
import tn.oliveconnect.harvest.HarvestRequestDto;
import tn.oliveconnect.mill.MillDto;
import tn.oliveconnect.resource.ResourceAvailabilityItemDto;

public record PlanningSuggestionsDto(
        LocalDate date,
        List<HarvestRequestDto> requests,
        List<ResourceAvailabilityItemDto> availableWorkers,
        List<ResourceAvailabilityItemDto> availableEquipment,
        List<TeamLeaderOptionDto> teamLeaders,
        List<MillDto> mills,
        List<PlanningGroupDto> groups,
        MillDto suggestedMill) {
}
