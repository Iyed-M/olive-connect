package tn.oliveconnect.round;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import tn.oliveconnect.common.StatusChange;

@Document("collection_rounds")
@CompoundIndex(name = "date_status", def = "{ 'date': 1, 'status': 1 }")
@CompoundIndex(name = "team_leader_date", def = "{ 'teamLeaderId': 1, 'date': -1 }")
@CompoundIndex(name = "mill_date_start_time", def = "{ 'millId': 1, 'date': 1, 'startTime': 1 }")
@CompoundIndex(name = "stops_harvest_request_id", def = "{ 'stops.harvestRequestId': 1 }")
@CompoundIndex(name = "worker_ids", def = "{ 'workerIds': 1 }")
@CompoundIndex(name = "equipment_ids", def = "{ 'equipmentIds': 1 }")
public class CollectionRound {

    @Id
    private String id;
    @Indexed(unique = true)
    private String code;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private RoundStatus status = RoundStatus.PLANNED;
    private String plannedById;
    private String teamLeaderId;
    private String millId;
    private List<String> workerIds = new ArrayList<>();
    private List<String> equipmentIds = new ArrayList<>();
    private List<RoundStop> stops = new ArrayList<>();
    private double estimatedTotalKg;
    private double totalHarvestedKg;
    private Instant startedAt;
    private Instant completedAt;
    private List<StatusChange> statusHistory = new ArrayList<>();
    private String season;
    @Version
    private Long version;

    public String getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public RoundStatus getStatus() { return status; }
    public void setStatus(RoundStatus status) { this.status = status; }
    public String getPlannedById() { return plannedById; }
    public void setPlannedById(String plannedById) { this.plannedById = plannedById; }
    public String getTeamLeaderId() { return teamLeaderId; }
    public void setTeamLeaderId(String teamLeaderId) { this.teamLeaderId = teamLeaderId; }
    public String getMillId() { return millId; }
    public void setMillId(String millId) { this.millId = millId; }
    public List<String> getWorkerIds() { return workerIds; }
    public void setWorkerIds(List<String> workerIds) {
        this.workerIds = workerIds == null ? new ArrayList<>() : new ArrayList<>(workerIds);
    }
    public List<String> getEquipmentIds() { return equipmentIds; }
    public void setEquipmentIds(List<String> equipmentIds) {
        this.equipmentIds = equipmentIds == null ? new ArrayList<>() : new ArrayList<>(equipmentIds);
    }
    public List<RoundStop> getStops() { return stops; }
    public void setStops(List<RoundStop> stops) {
        this.stops = stops == null ? new ArrayList<>() : new ArrayList<>(stops);
    }
    public double getEstimatedTotalKg() { return estimatedTotalKg; }
    public void setEstimatedTotalKg(double estimatedTotalKg) { this.estimatedTotalKg = estimatedTotalKg; }
    public double getTotalHarvestedKg() { return totalHarvestedKg; }
    public void setTotalHarvestedKg(double totalHarvestedKg) { this.totalHarvestedKg = totalHarvestedKg; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public List<StatusChange> getStatusHistory() { return statusHistory; }
    public void setStatusHistory(List<StatusChange> statusHistory) {
        this.statusHistory = statusHistory == null ? new ArrayList<>() : new ArrayList<>(statusHistory);
    }
    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }
    public Long getVersion() { return version; }

    public void transitionTo(RoundStatus next, String byUserId, String comment) {
        if (!canTransitionTo(next)) {
            String current = status == null ? "<new>" : status.name();
            throw new IllegalStateException("Cannot transition collection round from " + current + " to " + next);
        }
        String previous = status == null ? null : status.name();
        status = next;
        statusHistory.add(new StatusChange(previous, next.name(), Instant.now(), byUserId, comment));
    }

    public boolean canTransitionTo(RoundStatus next) {
        if (next == null || status == null) {
            return status == null && next == RoundStatus.PLANNED;
        }
        return switch (status) {
            case PLANNED -> next == RoundStatus.PLANNED || next == RoundStatus.IN_PROGRESS
                    || next == RoundStatus.CANCELLED;
            case IN_PROGRESS -> next == RoundStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    public void start(String byUserId) {
        transitionTo(RoundStatus.IN_PROGRESS, byUserId, null);
        startedAt = Instant.now();
    }

    public void complete(String byUserId) {
        if (stops.stream().anyMatch(stop -> stop.getStatus() == StopStatus.PENDING
                || stop.getStatus() == StopStatus.IN_PROGRESS)) {
            throw new IllegalStateException("A round cannot be completed while it has pending stops");
        }
        transitionTo(RoundStatus.COMPLETED, byUserId, null);
        completedAt = Instant.now();
        totalHarvestedKg = stops.stream().filter(stop -> stop.getHarvestedKg() != null)
                .mapToDouble(stop -> stop.getHarvestedKg()).sum();
    }

    public void removeStopForRequest(String harvestRequestId) {
        stops.removeIf(stop -> harvestRequestId.equals(stop.getHarvestRequestId()));
        for (int i = 0; i < stops.size(); i++) {
            stops.get(i).setOrder(i + 1);
        }
        estimatedTotalKg = stops.stream().mapToDouble(RoundStop::getEstimatedKg).sum();
    }
}
