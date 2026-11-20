package tn.oliveconnect.round;

import java.time.LocalTime;
import tn.oliveconnect.orchard.GeoPointDto;

public record RoundStopDto(
        int order,
        String harvestRequestId,
        String orchardId,
        String farmerId,
        String orchardName,
        GeoPointDto location,
        LocalTime plannedArrival,
        double estimatedKg,
        StopStatus status,
        Double harvestedKg,
        Integer crateCount,
        String comment) {

    public static RoundStopDto from(RoundStop stop) {
        return new RoundStopDto(stop.getOrder(), stop.getHarvestRequestId(), stop.getOrchardId(), stop.getFarmerId(),
                stop.getOrchardName(), stop.getLocation() == null ? null
                        : new GeoPointDto(stop.getLocation().getType(), stop.getLocation().getCoordinates()),
                stop.getPlannedArrival(), stop.getEstimatedKg(), stop.getStatus(), stop.getHarvestedKg(),
                stop.getCrateCount(), stop.getComment());
    }
}
