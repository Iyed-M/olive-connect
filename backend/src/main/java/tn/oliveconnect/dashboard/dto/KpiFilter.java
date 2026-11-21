package tn.oliveconnect.dashboard.dto;

import java.time.Instant;

/** Normalised dimensions shared by every dashboard aggregation. */
public record KpiFilter(
        String season,
        Instant from,
        Instant to,
        String varietyCode,
        String delegation,
        String millId) {
}
