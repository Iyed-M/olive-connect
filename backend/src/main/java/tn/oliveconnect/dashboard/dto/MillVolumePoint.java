package tn.oliveconnect.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MillVolumePoint(
        String millId,
        String date,
        double totalNetKg,
        long receptions,
        Map<String, Long> qualityDistribution,
        Double capacityUsePct) {
}
