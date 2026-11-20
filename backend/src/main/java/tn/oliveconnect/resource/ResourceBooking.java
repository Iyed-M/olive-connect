package tn.oliveconnect.resource;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("resource_bookings")
@CompoundIndex(name = "resource_id_start_end_active", def = "{ 'resourceId': 1, 'start': 1, 'end': 1 }",
        partialFilter = "{ 'status': 'ACTIVE' }")
@CompoundIndex(name = "start_resource_type", def = "{ 'start': 1, 'resourceType': 1 }")
public class ResourceBooking {

    @Id
    private String id;
    private ResourceKind resourceKind;
    private String resourceType;
    @Indexed
    private String resourceId;
    @Indexed(name = "round_id")
    private String roundId;
    private Instant start;
    private Instant end;
    private BookingStatus status = BookingStatus.ACTIVE;

    public static ResourceBooking forWorker(String resourceId, String roundId, Instant start, Instant end) {
        ResourceBooking booking = new ResourceBooking();
        booking.resourceKind = ResourceKind.WORKER;
        booking.resourceType = ResourceKind.WORKER.name();
        booking.resourceId = resourceId;
        booking.roundId = roundId;
        booking.start = start;
        booking.end = end;
        return booking;
    }

    public static ResourceBooking forEquipment(String resourceId, EquipmentType resourceType, String roundId,
                                               Instant start, Instant end) {
        ResourceBooking booking = new ResourceBooking();
        booking.resourceKind = ResourceKind.EQUIPMENT;
        booking.resourceType = resourceType == null ? null : resourceType.name();
        booking.resourceId = resourceId;
        booking.roundId = roundId;
        booking.start = start;
        booking.end = end;
        return booking;
    }

    public String getId() { return id; }
    public ResourceKind getResourceKind() { return resourceKind; }
    public void setResourceKind(ResourceKind resourceKind) { this.resourceKind = resourceKind; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getRoundId() { return roundId; }
    public void setRoundId(String roundId) { this.roundId = roundId; }
    public Instant getStart() { return start; }
    public void setStart(Instant start) { this.start = start; }
    public Instant getEnd() { return end; }
    public void setEnd(Instant end) { this.end = end; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
}
