package tn.oliveconnect.orchard;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("olive_varieties")
public class OliveVariety {

    @Id
    private String code;
    private String name;
    private double avgOilYieldPct;
    private double optimalMaturityMin;
    private double optimalMaturityMax;

    public OliveVariety() {
    }

    public OliveVariety(String code, String name, double avgOilYieldPct, double optimalMaturityMin,
                        double optimalMaturityMax) {
        this.code = code;
        this.name = name;
        this.avgOilYieldPct = avgOilYieldPct;
        this.optimalMaturityMin = optimalMaturityMin;
        this.optimalMaturityMax = optimalMaturityMax;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getAvgOilYieldPct() {
        return avgOilYieldPct;
    }

    public void setAvgOilYieldPct(double avgOilYieldPct) {
        this.avgOilYieldPct = avgOilYieldPct;
    }

    public double getOptimalMaturityMin() {
        return optimalMaturityMin;
    }

    public void setOptimalMaturityMin(double optimalMaturityMin) {
        this.optimalMaturityMin = optimalMaturityMin;
    }

    public double getOptimalMaturityMax() {
        return optimalMaturityMax;
    }

    public void setOptimalMaturityMax(double optimalMaturityMax) {
        this.optimalMaturityMax = optimalMaturityMax;
    }
}
