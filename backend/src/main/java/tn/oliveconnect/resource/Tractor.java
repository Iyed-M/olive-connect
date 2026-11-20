package tn.oliveconnect.resource;

public class Tractor extends Equipment {

    private String plateNumber;
    private int powerHp;

    public Tractor() {
        setType(EquipmentType.TRACTOR);
    }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
    public int getPowerHp() { return powerHp; }
    public void setPowerHp(int powerHp) { this.powerHp = powerHp; }
}
