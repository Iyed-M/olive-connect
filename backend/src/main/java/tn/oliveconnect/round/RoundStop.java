package tn.oliveconnect.round;

import java.time.LocalTime;
import tn.oliveconnect.harvest.HarvestRequest;
import tn.oliveconnect.orchard.GeoPoint;

/** One ordered orchard visit embedded in its CollectionRound. */
public class RoundStop {

    private int order;
    private String harvestRequestId;
    private String orchardId;
    private String farmerId;
    private String orchardName;
    private GeoPoint location;
    private LocalTime plannedArrival;
    private double estimatedKg;
    private StopStatus status = StopStatus.PENDING;
    private Double harvestedKg;
    private Integer crateCount;
    private String comment;

    public static RoundStop from(HarvestRequest request, int order, LocalTime plannedArrival) {
        RoundStop stop = new RoundStop();
        stop.order = order;
        stop.harvestRequestId = request.getId();
        stop.orchardId = request.getOrchardId();
        stop.farmerId = request.getFarmerId();
        if (request.getOrchard() != null) {
            stop.orchardName = request.getOrchard().getName();
            stop.location = request.getOrchard().getLocation();
        }
        stop.plannedArrival = plannedArrival;
        stop.estimatedKg = request.getEstimatedQuantityKg();
        return stop;
    }

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    public String getHarvestRequestId() { return harvestRequestId; }
    public void setHarvestRequestId(String harvestRequestId) { this.harvestRequestId = harvestRequestId; }
    public String getOrchardId() { return orchardId; }
    public void setOrchardId(String orchardId) { this.orchardId = orchardId; }
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public String getOrchardName() { return orchardName; }
    public void setOrchardName(String orchardName) { this.orchardName = orchardName; }
    public GeoPoint getLocation() { return location; }
    public void setLocation(GeoPoint location) { this.location = location; }
    public LocalTime getPlannedArrival() { return plannedArrival; }
    public void setPlannedArrival(LocalTime plannedArrival) { this.plannedArrival = plannedArrival; }
    public double getEstimatedKg() { return estimatedKg; }
    public void setEstimatedKg(double estimatedKg) { this.estimatedKg = estimatedKg; }
    public StopStatus getStatus() { return status; }
    public void setStatus(StopStatus status) { this.status = status; }
    public Double getHarvestedKg() { return harvestedKg; }
    public void setHarvestedKg(Double harvestedKg) { this.harvestedKg = harvestedKg; }
    public Integer getCrateCount() { return crateCount; }
    public void setCrateCount(Integer crateCount) { this.crateCount = crateCount; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
