package tn.oliveconnect.alert;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import tn.oliveconnect.common.StatusChange;
import tn.oliveconnect.orchard.GeoPoint;

/** A geolocated alert about an orchard (docs/05 §4.9). Minimal version: only what automatic rules need. */
@Document("field_alerts")
@CompoundIndex(name = "status_createdAt", def = "{ 'status': 1, 'createdAt': -1 }")
@CompoundIndex(name = "orchard_type_status", def = "{ 'orchardId': 1, 'type': 1, 'status': 1 }")
public class FieldAlert {

    @Id
    private String id;
    private AlertType type;
    private AlertSeverity severity;
    private AlertStatus status = AlertStatus.NEW;
    private AlertSource source;
    private String orchardId;
    private String orchardName;
    private String farmerId;
    @GeoSpatialIndexed(name = "location_2dsphere", type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoPoint location;
    private String description;
    private List<StatusChange> statusHistory = new ArrayList<>();
    private Instant createdAt = Instant.now();

    public String getId() {
        return id;
    }

    public AlertType getType() {
        return type;
    }

    public void setType(AlertType type) {
        this.type = type;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AlertSeverity severity) {
        this.severity = severity;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }

    public AlertSource getSource() {
        return source;
    }

    public void setSource(AlertSource source) {
        this.source = source;
    }

    public String getOrchardId() {
        return orchardId;
    }

    public void setOrchardId(String orchardId) {
        this.orchardId = orchardId;
    }

    public String getOrchardName() {
        return orchardName;
    }

    public void setOrchardName(String orchardName) {
        this.orchardName = orchardName;
    }

    public String getFarmerId() {
        return farmerId;
    }

    public void setFarmerId(String farmerId) {
        this.farmerId = farmerId;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public void setLocation(GeoPoint location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<StatusChange> getStatusHistory() {
        return statusHistory;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
