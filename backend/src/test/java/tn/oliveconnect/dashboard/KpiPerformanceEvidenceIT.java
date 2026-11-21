package tn.oliveconnect.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.dashboard.dto.KpiFilter;
import tn.oliveconnect.mill.MillReception;
import tn.oliveconnect.mill.QualityAssessment;
import tn.oliveconnect.mill.QualityGrade;

/** Explicit M11 evidence run. It is named IT so the normal unit/verify pass does not load 90,000 facts. */
class KpiPerformanceEvidenceIT extends AbstractIntegrationTest {

    private static final int RECEPTIONS = 90_000;
    private static final int ORCHARDS = 1_500;
    private static final String SEASON = "2025-2026";

    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    KpiRepositoryCustom kpiRepository;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new Query(), MillReception.class);
    }

    @Test
    void historyPipelinesReportTimingsAndExplainStats() {
        seedHistoryFacts();
        KpiFilter filter = new KpiFilter(SEASON, Instant.parse("2025-10-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"), null, null, null);

        // Warm Mongo's plan cache before recording the numbers used in the report.
        kpiRepository.aggregateSummary(filter);
        kpiRepository.aggregateYield(filter, "orchard");
        kpiRepository.aggregateLeadTime(filter, "delegation");
        kpiRepository.aggregateFillRate(filter, "week");
        kpiRepository.aggregateMillVolumes(filter);

        measure("summary", () -> kpiRepository.aggregateSummary(filter));
        measure("yield/orchard", () -> kpiRepository.aggregateYield(filter, "orchard"));
        measure("lead-time/delegation", () -> kpiRepository.aggregateLeadTime(filter, "delegation"));
        measure("fill-rate/week", () -> kpiRepository.aggregateFillRate(filter, "week"));
        measure("mill-volumes/day", () -> kpiRepository.aggregateMillVolumes(filter));
        explainPipelines();
    }

    private void seedHistoryFacts() {
        List<MillReception> batch = new ArrayList<>(1_000);
        Instant start = Instant.parse("2025-10-01T00:00:00Z");
        for (int i = 0; i < RECEPTIONS; i++) {
            MillReception reception = new MillReception();
            reception.setMillId("history-mill-" + (i % 5));
            reception.setRoundId("history-round-" + i);
            reception.setBinId("history-bin-" + (i % 200));
            reception.setOrchardId("history-orchard-" + (i % ORCHARDS));
            reception.setFarmerId("history-farmer-" + (i % ORCHARDS));
            reception.setHarvestRequestId("history-request-" + i);
            double net = 1_800 + (i % 2_000);
            reception.setGrossWeightKg(net + 650);
            reception.setTareKg(650);
            reception.setBinCapacityKg(4_000);
            reception.computeNetAndFillRate();
            QualityAssessment quality = new QualityAssessment();
            quality.setGrade(QualityGrade.values()[i % QualityGrade.values().length]);
            quality.setEstimatedOilYieldPct(18 + (i % 40) / 10.0);
            reception.setQuality(quality);
            reception.setSeason(SEASON);
            reception.setVarietyCode(i % 2 == 0 ? "CHEMLALI" : "CHETOUI");
            reception.setDelegation(i % 3 == 0 ? "SAKIET_EZZIT" : "AGAREB");
            reception.setOrchardAreaHa(1 + (i % 6));
            reception.setRequestValidatedAt(start.plusSeconds((i % 90) * 3_600L));
            reception.setLeadTimeHours(24 + i % 72);
            reception.setReceivedAt(start.plusSeconds((i % (92 * 24)) * 3_600L));
            reception.setReceivedById("history-manager");
            batch.add(reception);
            if (batch.size() == 1_000) {
                mongoTemplate.bulkOps(BulkMode.UNORDERED, MillReception.class).insert(batch).execute();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            mongoTemplate.bulkOps(BulkMode.UNORDERED, MillReception.class).insert(batch).execute();
        }
        assertThat(mongoTemplate.count(new Query(), MillReception.class)).isEqualTo(RECEPTIONS);
    }

    private void measure(String name, Supplier<Object> query) {
        long started = System.nanoTime();
        Object result = query.get();
        long millis = Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertThat(result).isNotNull();
        assertThat(millis).as(name + " must remain under one second").isLessThan(1_000);
        System.out.printf("M11 KPI timing %-24s %4d ms%n", name, millis);
    }

    private void explainPipelines() {
        Document season = new Document("$match", new Document("season", SEASON));
        explain("summary", List.of(season, new Document("$group",
                new Document("_id", null).append("totalNetKg", new Document("$sum", "$netWeightKg")))));
        explain("yield/orchard", List.of(season, new Document("$group",
                new Document("_id", "$orchardId").append("totalNetKg", new Document("$sum", "$netWeightKg")))));
        explain("lead-time/delegation", List.of(season, new Document("$group",
                new Document("_id", "$delegation").append("avgLeadTimeH", new Document("$avg", "$leadTimeHours")))));
        explain("fill-rate/week", List.of(season, new Document("$group", new Document("_id",
                new Document("week", new Document("$isoWeek", "$receivedAt")))
                .append("avgFillRate", new Document("$avg", "$fillRate")))));
        explain("mill-volumes/day", List.of(season, new Document("$group", new Document("_id",
                new Document("millId", "$millId").append("date", new Document("$dateTrunc",
                        new Document("date", "$receivedAt").append("unit", "day"))))
                .append("totalNetKg", new Document("$sum", "$netWeightKg")))));
    }

    private void explain(String name, List<Document> pipeline) {
        Document command = new Document("explain", new Document("aggregate", "mill_receptions")
                .append("pipeline", pipeline).append("cursor", new Document()))
                .append("verbosity", "executionStats");
        Document explain = mongoTemplate.executeCommand(command);
        Document stats = explain.get("executionStats", Document.class);
        System.out.printf("M11 KPI explain %-24s docs=%s keys=%s%n", name,
                stats == null ? "n/a" : stats.get("totalDocsExamined"),
                stats == null ? "n/a" : stats.get("totalKeysExamined"));
    }
}
