package tn.oliveconnect.map;

import java.util.List;

/** GeoJSON FeatureCollection returned by GET /api/map/public. */
public record PublicFeatureCollection(String type, List<PublicOrchardFeature> features) {

    public PublicFeatureCollection(List<PublicOrchardFeature> features) {
        this("FeatureCollection", features);
    }
}
