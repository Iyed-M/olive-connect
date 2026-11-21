package tn.oliveconnect.dashboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;
import tn.oliveconnect.dashboard.dto.FillRatePoint;
import tn.oliveconnect.dashboard.dto.KpiFilter;
import tn.oliveconnect.dashboard.dto.KpiSummaryDto;
import tn.oliveconnect.dashboard.dto.LeadTimePoint;
import tn.oliveconnect.dashboard.dto.MillVolumePoint;
import tn.oliveconnect.dashboard.dto.YieldPoint;
import tn.oliveconnect.mill.MillReception;

@Repository
public class KpiRepositoryCustomImpl implements KpiRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public KpiRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public KpiSummaryDto aggregateSummary(KpiFilter filter) {
        Document group = new Document("_id", null)
                .append("receptions", new Document("$sum", 1))
                .append("totalNetKg", new Document("$sum", "$netWeightKg"))
                .append("estOilKg", new Document("$sum", estimatedOilExpression()))
                .append("avgLeadTimeH", new Document("$avg", "$leadTimeHours"))
                .append("avgFillRate", new Document("$avg", "$fillRate"))
                .append("p90LeadTimeH", percentile("$leadTimeHours"));
        Document project = new Document("_id", 0)
                .append("receptions", 1)
                .append("totalNetKg", 1)
                .append("estOilKg", 1)
                .append("avgLeadTimeH", 1)
                .append("avgFillRate", 1)
                .append("p90LeadTimeH", new Document("$arrayElemAt", List.of("$p90LeadTimeH", 0)));
        List<Document> rows = aggregate(filter, new Document("$group", group), new Document("$project", project));
        if (rows.isEmpty()) {
            return new KpiSummaryDto(filter.season(), 0, 0, 0, 0, 0, 0);
        }
        Document row = rows.get(0);
        return new KpiSummaryDto(filter.season(), integer(row.get("receptions")), number(row.get("totalNetKg")),
                number(row.get("estOilKg")), number(row.get("avgLeadTimeH")), number(row.get("p90LeadTimeH")),
                number(row.get("avgFillRate")));
    }

    @Override
    public List<YieldPoint> aggregateYield(KpiFilter filter, String groupBy) {
        String groupField = switch (groupBy) {
            case "variety" -> "$varietyCode";
            case "delegation" -> "$delegation";
            default -> "$orchardId";
        };
        Document group = new Document("_id", groupField)
                .append("totalNetKg", new Document("$sum", "$netWeightKg"))
                .append("estOilKg", new Document("$sum", estimatedOilExpression()))
                .append("variety", new Document("$first", "$varietyCode"))
                .append("delegation", new Document("$first", "$delegation"))
                // $addToSet avoids multiplying an orchard's area by its number of receptions.
                .append("orchards", new Document("$addToSet", new Document("id", "$orchardId")
                        .append("areaHa", "$orchardAreaHa")));
        Document project = new Document("_id", 1)
                .append("totalNetKg", 1)
                .append("estOilKg", 1)
                .append("variety", 1)
                .append("delegation", 1)
                .append("areaHa", new Document("$reduce", new Document("input", "$orchards")
                        .append("initialValue", 0)
                        .append("in", new Document("$add", List.of("$$value", "$$this.areaHa")))));
        Document set = new Document("yieldKgHa", new Document("$cond", Arrays.asList(
                new Document("$gt", List.of("$areaHa", 0)),
                new Document("$divide", List.of("$totalNetKg", "$areaHa")),
                null)));
        List<Document> rows = aggregate(filter, new Document("$group", group),
                new Document("$project", project), new Document("$set", set),
                new Document("$sort", new Document("yieldKgHa", -1)));
        return rows.stream().map(row -> {
            String key = string(row.get("_id"));
            return new YieldPoint(key, groupBy, "orchard".equals(groupBy) ? key : null,
                    string(row.get("variety")), string(row.get("delegation")), number(row.get("areaHa")),
                    number(row.get("totalNetKg")), number(row.get("estOilKg")), nullableNumber(row.get("yieldKgHa")));
        }).toList();
    }

    @Override
    public List<LeadTimePoint> aggregateLeadTime(KpiFilter filter, String groupBy) {
        Object groupId = "week".equals(groupBy)
                ? new Document("week", new Document("$isoWeek", "$receivedAt"))
                        .append("year", new Document("$isoWeekYear", "$receivedAt"))
                : "$delegation";
        Document group = new Document("_id", groupId)
                .append("avgLeadTimeH", new Document("$avg", "$leadTimeHours"))
                .append("p90LeadTimeH", percentile("$leadTimeHours"))
                .append("n", new Document("$sum", 1));
        List<Document> rows = aggregate(filter, new Document("$group", group),
                new Document("$project", new Document("_id", 1)
                        .append("avgLeadTimeH", 1)
                        .append("p90LeadTimeH", new Document("$arrayElemAt", List.of("$p90LeadTimeH", 0)))
                        .append("n", 1)),
                new Document("$sort", "week".equals(groupBy)
                        ? new Document("_id.year", 1).append("_id.week", 1)
                        : new Document("_id", 1)));
        return rows.stream().map(row -> {
            Object rawId = row.get("_id");
            Document id = rawId instanceof Document nested ? nested : null;
            Integer year = id == null ? null : integer(id.get("year"));
            Integer week = id == null ? null : integer(id.get("week"));
            String delegation = id == null ? string(rawId) : null;
            String key = id == null ? delegation : year + "-W" + String.format("%02d", week);
            return new LeadTimePoint(key, delegation, year, week, number(row.get("avgLeadTimeH")),
                    number(row.get("p90LeadTimeH")), integer(row.get("n")));
        }).toList();
    }

    @Override
    public List<FillRatePoint> aggregateFillRate(KpiFilter filter, String groupBy) {
        String groupField = switch (groupBy) {
            case "bin" -> "$binId";
            case "round" -> "$roundId";
            default -> null;
        };

        Document groupId = groupField == null
                ? new Document("week", new Document("$isoWeek", "$receivedAt"))
                        .append("year", new Document("$isoWeekYear", "$receivedAt"))
                : new Document("key", groupField);
        Document underFilled = new Document("$cond", List.of(
                new Document("$lt", List.of("$fillRate", 0.7)), 1, 0));
        Document group = new Document("_id", groupId)
                .append("avgFillRate", new Document("$avg", "$fillRate"))
                .append("receptions", new Document("$sum", 1))
                .append("underFilled", new Document("$sum", underFilled));
        if ("bin".equals(groupBy)) {
            group.append("binId", new Document("$first", "$binId"));
        } else if ("round".equals(groupBy)) {
            group.append("roundId", new Document("$first", "$roundId"));
        }

        return aggregate(filter, new Document("$group", group), new Document("$sort", "week".equals(groupBy)
                ? new Document("_id.year", 1).append("_id.week", 1)
                : new Document("_id", 1))).stream().map(this::fillRatePoint).toList();
    }

    private FillRatePoint fillRatePoint(Document document) {
        Object rawId = document.get("_id");
        Document id = rawId instanceof Document nested ? nested : null;
        Integer year = id == null ? null : integer(id.get("year"));
        Integer week = id == null ? null : integer(id.get("week"));
        String key = id == null ? string(rawId) : string(id.get("key"));
        if (key == null && year != null && week != null) {
            key = year + "-W" + String.format("%02d", week);
        }
        return new FillRatePoint(key, year, week, number(document.get("avgFillRate")),
                integer(document.get("receptions")), integer(document.get("underFilled")),
                string(document.get("binId")), string(document.get("roundId")));
    }

    @Override
    public List<MillVolumePoint> aggregateMillVolumes(KpiFilter filter) {
        Document date = new Document("$dateTrunc", new Document("date", "$receivedAt").append("unit", "day"));
        Document group = new Document("_id", new Document("millId", "$millId").append("date", date))
                .append("totalNetKg", new Document("$sum", "$netWeightKg"))
                .append("receptions", new Document("$sum", 1));
        for (String grade : List.of("EXTRA", "GOOD", "AVERAGE", "POOR")) {
            group.append("quality_" + grade,
                    new Document("$sum", new Document("$cond", List.of(
                            new Document("$eq", List.of("$quality.grade", grade)), 1, 0))));
        }
        List<Document> rows = aggregate(filter, new Document("$group", group),
                new Document("$sort", new Document("_id.date", 1).append("_id.millId", 1)));
        return rows.stream().map(row -> {
            Document id = (Document) row.get("_id");
            Map<String, Long> quality = new LinkedHashMap<>();
            for (String grade : List.of("EXTRA", "GOOD", "AVERAGE", "POOR")) {
                quality.put(grade, (long) integer(row.get("quality_" + grade)));
            }
            return new MillVolumePoint(string(id.get("millId")), dateString(id.get("date")),
                    number(row.get("totalNetKg")), integer(row.get("receptions")), quality, null);
        }).toList();
    }

    private List<Document> aggregate(KpiFilter filter, Document... stages) {
        List<AggregationOperation> operations = new ArrayList<>();
        operations.add(Aggregation.match(criteria(filter)));
        for (Document stage : stages) {
            operations.add(raw(stage));
        }
        return mongoTemplate.aggregate(Aggregation.newAggregation(operations), MillReception.class, Document.class)
                .getMappedResults();
    }

    private Document estimatedOilExpression() {
        return new Document("$multiply", List.of("$netWeightKg",
                new Document("$divide", List.of("$quality.estimatedOilYieldPct", 100))));
    }

    private Document percentile(String field) {
        return new Document("$percentile", new Document("input", field)
                .append("p", List.of(0.9)).append("method", "approximate"));
    }

    private Criteria criteria(KpiFilter filter) {
        List<Criteria> criteria = new ArrayList<>();
        if (filter.season() != null) {
            criteria.add(Criteria.where("season").is(filter.season()));
        }
        if (filter.varietyCode() != null) {
            criteria.add(Criteria.where("varietyCode").is(filter.varietyCode()));
        }
        if (filter.delegation() != null) {
            criteria.add(Criteria.where("delegation").is(filter.delegation()));
        }
        if (filter.millId() != null) {
            criteria.add(Criteria.where("millId").is(filter.millId()));
        }
        if (filter.from() != null || filter.to() != null) {
            Criteria receivedAt = Criteria.where("receivedAt");
            if (filter.from() != null) {
                receivedAt.gte(filter.from());
            }
            if (filter.to() != null) {
                receivedAt.lt(filter.to());
            }
            criteria.add(receivedAt);
        }
        return criteria.isEmpty() ? new Criteria() : new Criteria().andOperator(criteria);
    }

    private AggregationOperation raw(Document stage) {
        return context -> stage;
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private static Double nullableNumber(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static String dateString(Object value) {
        if (value instanceof java.util.Date date) {
            return date.toInstant().toString().substring(0, 10);
        }
        return string(value);
    }
}
