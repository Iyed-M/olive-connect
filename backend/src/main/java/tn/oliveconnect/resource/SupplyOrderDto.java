package tn.oliveconnect.resource;

import java.time.LocalDate;

public record SupplyOrderDto(String id, String toolId, String toolCode, ToolType toolType, int quantity,
                             String supplier, LocalDate orderedAt, LocalDate expectedAt, LocalDate receivedAt,
                             SupplyStatus status) {

    static SupplyOrderDto from(SupplyOrder order, Tool tool) {
        return new SupplyOrderDto(order.getId(), order.getToolId(), tool == null ? null : tool.getCode(),
                tool == null ? null : tool.getToolType(), order.getQuantity(), order.getSupplier(),
                order.getOrderedAt(), order.getExpectedAt(), order.getReceivedAt(), order.getStatus());
    }
}
