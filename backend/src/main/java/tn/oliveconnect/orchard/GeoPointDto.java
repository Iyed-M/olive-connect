package tn.oliveconnect.orchard;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** GeoJSON Point received from the browser. Coordinates are [longitude, latitude]. */
public record GeoPointDto(
        String type,
        @NotNull @Size(min = 2, max = 2) List<@NotNull Double> coordinates) {

    public GeoPointDto {
        if (type == null || type.isBlank()) {
            type = "Point";
        }
    }

    static GeoPointDto from(GeoPoint point) {
        return point == null ? null : new GeoPointDto(point.getType(), point.getCoordinates());
    }

    GeoPoint toEntity() {
        return new GeoPoint(coordinates.get(0), coordinates.get(1));
    }
}
