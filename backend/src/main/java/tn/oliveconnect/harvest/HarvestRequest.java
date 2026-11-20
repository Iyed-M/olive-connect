package tn.oliveconnect.harvest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import tn.oliveconnect.common.StatusChange;
import tn.oliveconnect.user.Role;
import tn.oliveconnect.user.User;

@Document("harvest_requests")
@CompoundIndex(name = "farmer_created_at", def = "{ 'farmerId': 1, 'createdAt': -1 }")
@CompoundIndex(name = "status_priority_score", def = "{ 'status': 1, 'priorityScore': -1 }")
@CompoundIndex(name = "orchard_location_status", def = "{ 'orchard.location': '2dsphere', 'status': 1 }")
@CompoundIndex(name = "orchard_active_request", def = "{ 'orchardId': 1, 'status': 1 }",
        partialFilter = "{ 'status': { '$in': [ 'SUBMITTED', 'VALIDATED', 'SCHEDULED', 'IN_PROGRESS' ] } }")
public class HarvestRequest {

    @Id
    private String id;
    @Indexed(name = "round_id")
    private String roundId;
    private String farmerId;
    private String orchardId;
    private OrchardSnapshot orchard;
    private HarvestType type;
    private LocalDate desiredFrom;
    private LocalDate desiredTo;
    private double estimatedQuantityKg;
    private String comment;
    private HarvestRequestStatus status;
    private String rejectionReason;
    private double priorityScore;
    private List<StatusChange> statusHistory = new ArrayList<>();
    private String season;
    @CreatedDate
    private Instant createdAt;
    private Instant validatedAt;
    @Version
    private Long version;

    public String getId() {
        return id;
    }

    public String getRoundId() {
        return roundId;
    }

    public void setRoundId(String roundId) {
        this.roundId = roundId;
    }

    public String getFarmerId() {
        return farmerId;
    }

    public void setFarmerId(String farmerId) {
        this.farmerId = farmerId;
    }

    public String getOrchardId() {
        return orchardId;
    }

    public void setOrchardId(String orchardId) {
        this.orchardId = orchardId;
    }

    public OrchardSnapshot getOrchard() {
        return orchard;
    }

    public void setOrchard(OrchardSnapshot orchard) {
        this.orchard = orchard;
    }

    public HarvestType getType() {
        return type;
    }

    public void setType(HarvestType type) {
        this.type = type;
    }

    public LocalDate getDesiredFrom() {
        return desiredFrom;
    }

    public void setDesiredFrom(LocalDate desiredFrom) {
        this.desiredFrom = desiredFrom;
    }

    public LocalDate getDesiredTo() {
        return desiredTo;
    }

    public void setDesiredTo(LocalDate desiredTo) {
        this.desiredTo = desiredTo;
    }

    public double getEstimatedQuantityKg() {
        return estimatedQuantityKg;
    }

    public void setEstimatedQuantityKg(double estimatedQuantityKg) {
        this.estimatedQuantityKg = estimatedQuantityKg;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public HarvestRequestStatus getStatus() {
        return status;
    }

    public void setStatus(HarvestRequestStatus status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public double getPriorityScore() {
        return priorityScore;
    }

    public void setPriorityScore(double priorityScore) {
        this.priorityScore = priorityScore;
    }

    public List<StatusChange> getStatusHistory() {
        return statusHistory;
    }

    public void setStatusHistory(List<StatusChange> statusHistory) {
        this.statusHistory = statusHistory == null ? new ArrayList<>() : new ArrayList<>(statusHistory);
    }

    public String getSeason() {
        return season;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(Instant validatedAt) {
        this.validatedAt = validatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public boolean canTransitionTo(HarvestRequestStatus next) {
        if (next == null || status == null) {
            return status == null && next == HarvestRequestStatus.SUBMITTED;
        }
        return switch (status) {
            case SUBMITTED -> next == HarvestRequestStatus.VALIDATED
                    || next == HarvestRequestStatus.REJECTED
                    || next == HarvestRequestStatus.CANCELLED;
            case VALIDATED -> next == HarvestRequestStatus.SCHEDULED || next == HarvestRequestStatus.CANCELLED;
            case SCHEDULED -> next == HarvestRequestStatus.VALIDATED
                    || next == HarvestRequestStatus.CANCELLED
                    || next == HarvestRequestStatus.IN_PROGRESS;
            case IN_PROGRESS -> next == HarvestRequestStatus.COMPLETED
                    || next == HarvestRequestStatus.VALIDATED;
            case REJECTED, COMPLETED, CANCELLED -> false;
        };
    }

    public boolean canBeModifiedBy(User user) {
        return user != null && user.getRoles().contains(Role.FARMER)
                && java.util.Objects.equals(farmerId, user.getId())
                && (status == HarvestRequestStatus.SUBMITTED || status == HarvestRequestStatus.VALIDATED);
    }

    public void transitionTo(HarvestRequestStatus next, User user) {
        transitionTo(next, user == null ? null : user.getId(), null);
    }

    public void transitionTo(HarvestRequestStatus next, String byUserId, String comment) {
        transitionTo(next, byUserId, comment, Instant.now());
    }

    public void transitionTo(HarvestRequestStatus next, String byUserId, String comment, Instant at) {
        if (!canTransitionTo(next)) {
            String current = status == null ? "<new>" : status.name();
            throw new IllegalStateException("Cannot transition harvest request from " + current + " to " + next);
        }
        String previous = status == null ? null : status.name();
        status = next;
        statusHistory.add(new StatusChange(previous, next.name(), at, byUserId, comment));
    }
}
