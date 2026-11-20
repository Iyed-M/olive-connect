package tn.oliveconnect.orchard;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OrchardDto(
        String id,
        String name,
        String ownerId,
        Map<String, String> owner,
        GeoPointDto location,
        String delegation,
        List<PlotDto> plots,
        double areaHa,
        int treeCount,
        String mainVarietyCode,
        HarvestStatus harvestStatus,
        double currentMaturityIndex,
        Instant maturityUpdatedAt,
        double lastSeasonYieldKgHa,
        Long version) {

    static OrchardDto from(Orchard orchard) {
        return new OrchardDto(orchard.getId(), orchard.getName(), orchard.getOwnerId(), orchard.getOwner(),
                GeoPointDto.from(orchard.getLocation()), orchard.getDelegation(),
                orchard.getPlots().stream().map(PlotDto::from).toList(), orchard.getAreaHa(), orchard.getTreeCount(),
                orchard.getMainVarietyCode(), orchard.getHarvestStatus(), orchard.getCurrentMaturityIndex(),
                orchard.getMaturityUpdatedAt(), orchard.getLastSeasonYieldKgHa(), orchard.getVersion());
    }
}
