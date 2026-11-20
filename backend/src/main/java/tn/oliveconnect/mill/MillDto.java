package tn.oliveconnect.mill;

import java.time.Instant;
import tn.oliveconnect.orchard.GeoPointDto;

public record MillDto(
        String id,
        String name,
        GeoPointDto location,
        String address,
        double dailyCapacityKg,
        double availableCapacityKg,
        Instant capacityUpdatedAt) {

    public static MillDto from(Mill mill) {
        return new MillDto(mill.getId(), mill.getName(),
                mill.getLocation() == null ? null
                        : new GeoPointDto(mill.getLocation().getType(), mill.getLocation().getCoordinates()),
                mill.getAddress(), mill.getDailyCapacityKg(), mill.getAvailableCapacityKg(),
                mill.getCapacityUpdatedAt());
    }
}
