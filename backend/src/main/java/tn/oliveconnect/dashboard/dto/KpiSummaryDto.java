package tn.oliveconnect.dashboard.dto;

public record KpiSummaryDto(
        String season,
        long receptions,
        double totalNetKg,
        double avgYieldKgHa,
        double estOilKg,
        double avgLeadTimeH,
        double p90LeadTimeH,
        double avgFillRate) {
}
