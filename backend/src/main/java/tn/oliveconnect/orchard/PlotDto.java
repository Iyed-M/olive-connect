package tn.oliveconnect.orchard;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record PlotDto(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 40) String varietyCode,
        @DecimalMin(value = "0.01") double areaHa,
        @Min(1) int treeCount,
        @Min(1800) @Max(2100) int plantingYear,
        boolean irrigated) {

    Plot toEntity() {
        Plot plot = new Plot();
        plot.setCode(code.trim());
        plot.setVarietyCode(varietyCode.trim().toUpperCase(Locale.ROOT));
        plot.setAreaHa(areaHa);
        plot.setTreeCount(treeCount);
        plot.setPlantingYear(plantingYear);
        plot.setIrrigated(irrigated);
        return plot;
    }

    static PlotDto from(Plot plot) {
        return new PlotDto(plot.getCode(), plot.getVarietyCode(), plot.getAreaHa(), plot.getTreeCount(),
                plot.getPlantingYear(), plot.isIrrigated());
    }
}
