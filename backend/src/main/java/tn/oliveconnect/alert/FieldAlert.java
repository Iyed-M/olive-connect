package tn.oliveconnect.alert;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import tn.oliveconnect.common.StatusChange;
import tn.oliveconnect.orchard.GeoPoint;

/** A geolocated farmer or sensor report about an orchard (docs/05 §4.9). */
@Document("field_alerts")
@CompoundIndex(name = "status_createdAt", def = "{ 'status': 1, 'createdAt': -1 }")
@CompoundIndex(name = "location_status", def = "{ 'location': '2dsphere', 'status': 1 }")
@CompoundIndex(name = "farmer_createdAt", def = "{ 'farmerId': 1, 'createdAt': -1 }")
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
    private String reporterId;
    @Indexed(name = "open_alert_key", unique = true, partialFilter = "{ 'openAlertKey': { '$type': 'string' } }")
    private String openAlertKey;
    @GeoSpatialIndexed(name = "location_2dsphere", type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoPoint location;
    private boolean locationMismatch;
    private String description;
    private List<String> photoUrls = new ArrayList<>();
    private String resolution;
    private String handledById;
    private String harvestRequestId;
    private List<StatusChange> statusHistory = new ArrayList<>();
    @CreatedDate
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
        if (status == AlertStatus.RESOLVED || status == AlertStatus.DISMISSED) {
            openAlertKey = null;
        }
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

    public String getReporterId() {
        return reporterId;
    }

    public void setReporterId(String reporterId) {
        this.reporterId = reporterId;
    }

    public String getOpenAlertKey() {
        return openAlertKey;
    }

    public void setOpenAlertKey(String openAlertKey) {
        this.openAlertKey = openAlertKey;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public void setLocation(GeoPoint location) {
        this.location = location;
    }

    public boolean isLocationMismatch() {
        return locationMismatch;
    }

    public void setLocationMismatch(boolean locationMismatch) {
        this.locationMismatch = locationMismatch;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getPhotoUrls() {
        return photoUrls;
    }

    public void setPhotoUrls(List<String> photoUrls) {
        this.photoUrls = photoUrls == null ? new ArrayList<>() : new ArrayList<>(photoUrls);
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getHandledById() {
        return handledById;
    }

    public void setHandledById(String handledById) {
        this.handledById = handledById;
    }

    public String getHarvestRequestId() {
        return harvestRequestId;
    }

    public void setHarvestRequestId(String harvestRequestId) {
        this.harvestRequestId = harvestRequestId;
    }

    public List<StatusChange> getStatusHistory() {
        return statusHistory;
    }

    public void setStatusHistory(List<StatusChange> statusHistory) {
        this.statusHistory = statusHistory == null ? new ArrayList<>() : new ArrayList<>(statusHistory);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public static String openAlertKey(String orchardId, AlertType type) {
        return orchardId + ":" + type.name();
    }

    public boolean canTransitionTo(AlertStatus next) {
        return switch (status) {
            case NEW -> next == AlertStatus.ACKNOWLEDGED || next == AlertStatus.DISMISSED;
            case ACKNOWLEDGED -> next == AlertStatus.RESOLVED || next == AlertStatus.DISMISSED;
            case RESOLVED, DISMISSED -> false;
        };
    }

    public void transitionTo(AlertStatus next, String byUserId, String comment) {
        if (!canTransitionTo(next)) {
            throw new IllegalStateException("Cannot transition field alert from " + status + " to " + next);
        }
        String previous = status == null ? null : status.name();
        status = next;
        if (next == AlertStatus.RESOLVED || next == AlertStatus.DISMISSED) {
            openAlertKey = null;
        }
        statusHistory.add(new StatusChange(previous, next.name(), Instant.now(), byUserId, comment));
    }
}
