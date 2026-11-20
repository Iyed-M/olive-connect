package tn.oliveconnect.harvest;

import tn.oliveconnect.orchard.GeoPointDto;

public record OrchardSnapshotDto(
        String id,
        String name,
        GeoPointDto location,
        String delegation,
        String mainVarietyCode,
        double areaHa,
        double lastSeasonYieldKgHa,
        double currentMaturityIndex) {

    static OrchardSnapshotDto from(OrchardSnapshot snapshot) {
        GeoPointDto location = snapshot.getLocation() == null ? null
                : new GeoPointDto(snapshot.getLocation().getType(), snapshot.getLocation().getCoordinates());
        return new OrchardSnapshotDto(snapshot.getId(), snapshot.getName(), location,
                snapshot.getDelegation(), snapshot.getMainVarietyCode(), snapshot.getAreaHa(),
                snapshot.getLastSeasonYieldKgHa(), snapshot.getCurrentMaturityIndex());
    }
}
