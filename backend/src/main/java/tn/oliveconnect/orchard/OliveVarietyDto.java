package tn.oliveconnect.orchard;

public record OliveVarietyDto(
        String code,
        String name,
        double avgOilYieldPct,
        double optimalMaturityMin,
        double optimalMaturityMax) {

    static OliveVarietyDto from(OliveVariety variety) {
        return new OliveVarietyDto(variety.getCode(), variety.getName(), variety.getAvgOilYieldPct(),
                variety.getOptimalMaturityMin(), variety.getOptimalMaturityMax());
    }
}
