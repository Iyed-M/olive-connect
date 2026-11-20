package tn.oliveconnect.harvest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import tn.oliveconnect.common.StatusChange;

public record HarvestRequestDto(
        String id,
        String farmerId,
        OrchardSnapshotDto orchard,
        HarvestType type,
        LocalDate desiredFrom,
        LocalDate desiredTo,
        double estimatedQuantityKg,
        String comment,
        HarvestRequestStatus status,
        String rejectionReason,
        double priorityScore,
        String roundId,
        List<StatusChange> history,
        Instant createdAt,
        Instant validatedAt,
        List<String> allowedActions,
        Long version) {

    static HarvestRequestDto from(HarvestRequest request, List<String> allowedActions) {
        return new HarvestRequestDto(request.getId(), request.getFarmerId(),
                OrchardSnapshotDto.from(request.getOrchard()), request.getType(), request.getDesiredFrom(),
                request.getDesiredTo(), request.getEstimatedQuantityKg(), request.getComment(), request.getStatus(),
                request.getRejectionReason(), request.getPriorityScore(), request.getRoundId(),
                List.copyOf(request.getStatusHistory()), request.getCreatedAt(), request.getValidatedAt(),
                List.copyOf(allowedActions), request.getVersion());
    }
}
