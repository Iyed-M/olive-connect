package tn.oliveconnect.map;

import com.fasterxml.jackson.annotation.JsonInclude;
import tn.oliveconnect.orchard.GeoPoint;
import tn.oliveconnect.orchard.HarvestStatus;

/**
 * One GeoJSON Feature of the public map: a single orchard or a cluster of orchards.
 * Anonymous by design: no owner, no phone, no yield, not even the orchard name (it can contain a person's name).
 */
public record PublicOrchardFeature(String type, GeoPoint geometry, Properties properties) {

    /** Orchard fields are set for a single orchard, cluster and count for a cluster. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Properties(
            String id,
            String varietyCode,
            HarvestStatus harvestStatus,
            Double maturityIndex,
            Boolean cluster,
            Integer count) {
    }

    public static PublicOrchardFeature orchard(String id, GeoPoint location, String varietyCode,
            HarvestStatus status, double maturityIndex) {
        return new PublicOrchardFeature("Feature", location,
                new Properties(id, varietyCode, status, maturityIndex, null, null));
    }

    public static PublicOrchardFeature cluster(GeoPoint center, int count) {
        return new PublicOrchardFeature("Feature", center,
                new Properties(null, null, null, null, true, count));
    }
}
