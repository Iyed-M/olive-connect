package tn.oliveconnect.mill;

import java.time.Instant;

public record MillReceptionDto(
        String id,
        String millId,
        String roundId,
        String binId,
        String orchardId,
        String farmerId,
        String harvestRequestId,
        double grossWeightKg,
        double tareKg,
        double netWeightKg,
        double binCapacityKg,
        double fillRate,
        QualityAssessmentDto quality,
        String season,
        String varietyCode,
        String delegation,
        double orchardAreaHa,
        Instant requestValidatedAt,
        double leadTimeHours,
        Instant receivedAt,
        String receivedById) {

    public static MillReceptionDto from(MillReception reception) {
        return new MillReceptionDto(reception.getId(), reception.getMillId(), reception.getRoundId(),
                reception.getBinId(), reception.getOrchardId(), reception.getFarmerId(),
                reception.getHarvestRequestId(), reception.getGrossWeightKg(), reception.getTareKg(),
                reception.getNetWeightKg(), reception.getBinCapacityKg(), reception.getFillRate(),
                QualityAssessmentDto.from(reception.getQuality()), reception.getSeason(), reception.getVarietyCode(),
                reception.getDelegation(), reception.getOrchardAreaHa(), reception.getRequestValidatedAt(),
                reception.getLeadTimeHours(), reception.getReceivedAt(), reception.getReceivedById());
    }
}
