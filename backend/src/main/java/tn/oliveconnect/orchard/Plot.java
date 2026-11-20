package tn.oliveconnect.orchard;

/** A bounded plot embedded in its Orchard document. */
public class Plot {

    private String code;
    private String varietyCode;
    private double areaHa;
    private int treeCount;
    private int plantingYear;
    private boolean irrigated;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getVarietyCode() {
        return varietyCode;
    }

    public void setVarietyCode(String varietyCode) {
        this.varietyCode = varietyCode;
    }

    public double getAreaHa() {
        return areaHa;
    }

    public void setAreaHa(double areaHa) {
        this.areaHa = areaHa;
    }

    public int getTreeCount() {
        return treeCount;
    }

    public void setTreeCount(int treeCount) {
        this.treeCount = treeCount;
    }

    public int getPlantingYear() {
        return plantingYear;
    }

    public void setPlantingYear(int plantingYear) {
        this.plantingYear = plantingYear;
    }

    public boolean isIrrigated() {
        return irrigated;
    }

    public void setIrrigated(boolean irrigated) {
        this.irrigated = irrigated;
    }
}
