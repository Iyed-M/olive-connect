package tn.oliveconnect.round;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import tn.oliveconnect.common.Correction;
import tn.oliveconnect.common.StatusChange;

public record CollectionRoundDto(
        String id,
        String code,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        RoundStatus status,
        String plannedById,
        String teamLeaderId,
        String millId,
        List<String> workerIds,
        List<String> equipmentIds,
        List<RoundStopDto> stops,
        double estimatedTotalKg,
        double totalHarvestedKg,
        Instant startedAt,
        Instant completedAt,
        List<StatusChange> history,
        String season,
        List<Correction> corrections,
        Long version,
        List<PlanningWarningDto> warnings) {

    public static CollectionRoundDto from(CollectionRound round, List<PlanningWarningDto> warnings) {
        return new CollectionRoundDto(round.getId(), round.getCode(), round.getDate(), round.getStartTime(),
                round.getEndTime(), round.getStatus(), round.getPlannedById(), round.getTeamLeaderId(),
                round.getMillId(), List.copyOf(round.getWorkerIds()), List.copyOf(round.getEquipmentIds()),
                round.getStops().stream().map(RoundStopDto::from).toList(), round.getEstimatedTotalKg(),
                round.getTotalHarvestedKg(), round.getStartedAt(), round.getCompletedAt(),
                List.copyOf(round.getStatusHistory()), round.getSeason(),
                List.copyOf(round.getCorrections()), round.getVersion(),
                warnings == null ? List.of() : List.copyOf(warnings));
    }
}
