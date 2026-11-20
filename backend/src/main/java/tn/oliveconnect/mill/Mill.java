package tn.oliveconnect.mill;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import tn.oliveconnect.orchard.GeoPoint;

@Document("mills")
public class Mill {

    @Id
    private String id;
    @Indexed(unique = true)
    private String name;
    @GeoSpatialIndexed(name = "location_2dsphere", type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoPoint location;
    private String address;
    private double dailyCapacityKg;
    private double availableCapacityKg;
    private Instant capacityUpdatedAt;

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public GeoPoint getLocation() { return location; }
    public void setLocation(GeoPoint location) { this.location = location; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public double getDailyCapacityKg() { return dailyCapacityKg; }
    public void setDailyCapacityKg(double dailyCapacityKg) { this.dailyCapacityKg = dailyCapacityKg; }
    public double getAvailableCapacityKg() { return availableCapacityKg; }
    public void setAvailableCapacityKg(double availableCapacityKg) { this.availableCapacityKg = availableCapacityKg; }
    public Instant getCapacityUpdatedAt() { return capacityUpdatedAt; }
    public void setCapacityUpdatedAt(Instant capacityUpdatedAt) { this.capacityUpdatedAt = capacityUpdatedAt; }
}
