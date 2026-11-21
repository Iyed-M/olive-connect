package tn.oliveconnect.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeadTimePoint(
        String key,
        String delegation,
        Integer year,
        Integer week,
        double avgLeadTimeH,
        double p90LeadTimeH,
        long n) {
}
