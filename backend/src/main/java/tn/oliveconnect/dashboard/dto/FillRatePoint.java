package tn.oliveconnect.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FillRatePoint(
        String key,
        Integer year,
        Integer week,
        double avgFillRate,
        long receptions,
        long underFilled,
        String binId,
        String roundId) {
}
