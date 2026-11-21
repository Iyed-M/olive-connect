package tn.oliveconnect.imports;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import tn.oliveconnect.alert.AlertSeverity;
import tn.oliveconnect.alert.AlertSource;
import tn.oliveconnect.alert.AlertStatus;
import tn.oliveconnect.alert.AlertType;
import tn.oliveconnect.alert.FieldAlert;
import tn.oliveconnect.alert.FieldAlertRepository;
import tn.oliveconnect.orchard.HarvestStatus;
import tn.oliveconnect.orchard.OliveVariety;
import tn.oliveconnect.orchard.OliveVarietyRepository;
import tn.oliveconnect.orchard.Orchard;
import tn.oliveconnect.settings.SettingsService;

/**
 * Raises automatic field alerts after a dynamic import (docs/07 §4): heavy rain in a delegation (the highest 24 h total of one of its stations),
 * and maturity inside the optimal window of the orchard's main variety. An orchard never gets a
 * second open alert of the same type.
 */
@Service
public class AutoAlertService {

    private static final List<AlertStatus> OPEN = List.of(AlertStatus.NEW, AlertStatus.ACKNOWLEDGED);

    private final MongoTemplate mongoTemplate;
    private final FieldAlertRepository alertRepository;
    private final OliveVarietyRepository varietyRepository;
    private final SettingsService settingsService;

    public AutoAlertService(MongoTemplate mongoTemplate, FieldAlertRepository alertRepository,
            OliveVarietyRepository varietyRepository, SettingsService settingsService) {
        this.mongoTemplate = mongoTemplate;
        this.alertRepository = alertRepository;
        this.varietyRepository = varietyRepository;
        this.settingsService = settingsService;
    }

    public void evaluate() {
        evaluateRain();
        evaluateMaturity();
    }

    private void evaluateRain() {
        double threshold = settingsService.get().getRainAlertMm24h();
        Instant since = Instant.now().minus(Duration.ofHours(24));
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("timestamp").gte(since)),
                Aggregation.group("meta.delegation", "meta.stationId").sum("rainfallMm").as("stationMm"),
                Aggregation.group("_id.delegation").max("stationMm").as("rainMm"));
        AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, "weather_readings", Map.class);
        for (Map<?, ?> row : results.getMappedResults()) {
            Object delegation = row.get("_id");
            double rainMm = ((Number) row.get("rainMm")).doubleValue();
            if (delegation == null || rainMm < threshold) {
                continue;
            }
            List<Orchard> orchards = mongoTemplate.find(
                    Query.query(Criteria.where("delegation").is(delegation)), Orchard.class);
            for (Orchard orchard : orchards) {
                String description = "Rain: %.1f mm in the last 24 h in %s (threshold %.1f mm)"
                        .formatted(rainMm, delegation, threshold);
                raiseIfNoOpenAlert(orchard, AlertType.RAIN, AlertSeverity.MEDIUM, description);
            }
        }
    }

    private void evaluateMaturity() {
        Map<String, OliveVariety> varieties = varietyRepository.findAll().stream()
                .collect(Collectors.toMap(OliveVariety::getCode, Function.identity()));
        List<Orchard> orchards = mongoTemplate.find(
                Query.query(Criteria.where("harvestStatus").is(HarvestStatus.NOT_HARVESTED)), Orchard.class);
        for (Orchard orchard : orchards) {
            OliveVariety variety = varieties.get(orchard.getMainVarietyCode());
            if (variety == null) {
                continue;
            }
            double index = orchard.getCurrentMaturityIndex();
            if (index >= variety.getOptimalMaturityMin() && index <= variety.getOptimalMaturityMax()) {
                String description = "Maturity index %.1f is in the optimal window [%.1f, %.1f] of %s"
                        .formatted(index, variety.getOptimalMaturityMin(), variety.getOptimalMaturityMax(),
                                variety.getName());
                raiseIfNoOpenAlert(orchard, AlertType.MATURITY, AlertSeverity.HIGH, description);
            }
        }
    }

    private void raiseIfNoOpenAlert(Orchard orchard, AlertType type, AlertSeverity severity, String description) {
        if (alertRepository.existsByOrchardIdAndTypeAndStatusIn(orchard.getId(), type, OPEN)) {
            return;
        }
        FieldAlert alert = new FieldAlert();
        alert.setType(type);
        alert.setSeverity(severity);
        alert.setSource(AlertSource.SENSOR);
        alert.setOrchardId(orchard.getId());
        alert.setOrchardName(orchard.getName());
        alert.setFarmerId(orchard.getOwnerId());
        alert.setLocation(orchard.getLocation());
        alert.setDescription(description);
        alertRepository.save(alert);
    }
}
