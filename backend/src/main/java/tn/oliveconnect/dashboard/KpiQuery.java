package tn.oliveconnect.dashboard;

/** Raw query parameters accepted by the dashboard endpoints. */
public record KpiQuery(
        String season,
        String from,
        String to,
        String variety,
        String delegation,
        String millId,
        String groupBy) {
}
