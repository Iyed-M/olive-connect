package tn.oliveconnect.resource;

import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** An order of nets or shakers. Receiving it adds the quantity to the stock of the Tool. */
@Document("supply_orders")
@CompoundIndex(name = "status_ordered_at", def = "{ 'status': 1, 'orderedAt': -1 }")
public class SupplyOrder {

    @Id
    private String id;
    @Indexed(name = "tool_id")
    private String toolId;
    private int quantity;
    private String supplier;
    private LocalDate orderedAt;
    private LocalDate expectedAt;
    private LocalDate receivedAt;
    private SupplyStatus status = SupplyStatus.ORDERED;
    @Version
    private Long version;

    public String getId() { return id; }
    public String getToolId() { return toolId; }
    public void setToolId(String toolId) { this.toolId = toolId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }
    public LocalDate getOrderedAt() { return orderedAt; }
    public void setOrderedAt(LocalDate orderedAt) { this.orderedAt = orderedAt; }
    public LocalDate getExpectedAt() { return expectedAt; }
    public void setExpectedAt(LocalDate expectedAt) { this.expectedAt = expectedAt; }
    public LocalDate getReceivedAt() { return receivedAt; }
    public SupplyStatus getStatus() { return status; }

    /** ORDERED to RECEIVED, the only transition. */
    public void receive(LocalDate on) {
        if (status == SupplyStatus.RECEIVED) {
            throw new IllegalStateException("Supply order already received");
        }
        status = SupplyStatus.RECEIVED;
        receivedAt = on;
    }
}
