package tn.oliveconnect.resource;

/** A net or a shaker: tracked as a stock quantity, not one by one. "type" always equals the toolType. */
public class Tool extends Equipment {

    private ToolType toolType;
    private int stockQuantity;

    public Tool(ToolType toolType) {
        this.toolType = toolType;
        setType(EquipmentType.valueOf(toolType.name()));
    }

    public ToolType getToolType() { return toolType; }
    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }
}
