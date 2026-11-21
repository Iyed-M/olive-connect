package tn.oliveconnect.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record YieldPoint(
        String key,
        String groupBy,
        String orchardId,
        String variety,
        String delegation,
        double areaHa,
        double totalNetKg,
        double estOilKg,
        Double yieldKgHa) {
}
