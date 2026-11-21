package tn.oliveconnect.mill;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import tn.oliveconnect.orchard.GeoPoint;

/** An olive mill (docs/05 §4.8). Minimal version: the mill module adds managers and receptions later. */
@Document("mills")
public class Mill {

    @Id
    private String id;
    @Indexed(unique = true)
    private String code;
    private String name;
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoPoint location;
    private double dailyCapacityKg;
    private double availableCapacityKg;
    // date of the capacity file that set availableCapacityKg: an older file never overwrites a newer one
    private Instant capacityUpdatedAt;

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public void setLocation(GeoPoint location) {
        this.location = location;
    }

    public double getDailyCapacityKg() {
        return dailyCapacityKg;
    }

    public void setDailyCapacityKg(double dailyCapacityKg) {
        this.dailyCapacityKg = dailyCapacityKg;
    }

    public double getAvailableCapacityKg() {
        return availableCapacityKg;
    }

    public void setAvailableCapacityKg(double availableCapacityKg) {
        this.availableCapacityKg = availableCapacityKg;
    }

    public Instant getCapacityUpdatedAt() {
        return capacityUpdatedAt;
    }

    public void setCapacityUpdatedAt(Instant capacityUpdatedAt) {
        this.capacityUpdatedAt = capacityUpdatedAt;
    }
}
