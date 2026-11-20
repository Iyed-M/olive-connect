package tn.oliveconnect.resource;

public class Bin extends Equipment {

    private String serialNumber;
    private double capacityKg;

    public Bin() {
        setType(EquipmentType.BIN);
    }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public double getCapacityKg() { return capacityKg; }
    public void setCapacityKg(double capacityKg) { this.capacityKg = capacityKg; }
}
