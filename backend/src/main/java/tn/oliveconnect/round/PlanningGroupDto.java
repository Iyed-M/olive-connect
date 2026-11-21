package tn.oliveconnect.round;

import java.util.List;
import tn.oliveconnect.harvest.HarvestRequestDto;

public record PlanningGroupDto(List<HarvestRequestDto> requests, List<String> requestIds,
                               double estimatedTotalKg, String nearestMillId) {
}
