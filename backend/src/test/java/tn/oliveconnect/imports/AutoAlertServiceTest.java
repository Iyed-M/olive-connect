package tn.oliveconnect.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.alert.AlertSource;
import tn.oliveconnect.alert.AlertStatus;
import tn.oliveconnect.alert.AlertType;
import tn.oliveconnect.alert.FieldAlert;
import tn.oliveconnect.alert.FieldAlertRepository;
import tn.oliveconnect.orchard.GeoPoint;
import tn.oliveconnect.orchard.Orchard;
import tn.oliveconnect.orchard.Plot;
import tn.oliveconnect.settings.SystemSettings;

/** T-IM-4: automatic alert rules create one alert, never a duplicate. */
class AutoAlertServiceTest extends AbstractIntegrationTest {

    @Autowired
    AutoAlertService autoAlertService;
    @Autowired
    FieldAlertRepository alertRepository;
    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        alertRepository.deleteAll();
        mongoTemplate.remove(new Query(), Orchard.class);
        mongoTemplate.remove(new Query(), WeatherReading.class);
        mongoTemplate.remove(new Query(), SystemSettings.class);
    }

    @Test
    void heavyRainRaisesOneSensorAlertPerOrchardOfTheDelegationAndNoDuplicate() {
        Orchard inside = mongoTemplate.save(orchard("inside", "SAKIET_EZZIT", 0));
        mongoTemplate.save(orchard("elsewhere", "THYNA", 0));
        rain("SFX-01", "SAKIET_EZZIT", Instant.now().minus(Duration.ofHours(2)), 12);
        rain("SFX-01", "SAKIET_EZZIT", Instant.now().minus(Duration.ofHours(5)), 10);

        autoAlertService.evaluate();
        autoAlertService.evaluate();

        List<FieldAlert> alerts = alertRepository.findAll();
        assertThat(alerts).hasSize(1);
        FieldAlert alert = alerts.get(0);
        assertThat(alert.getType()).isEqualTo(AlertType.RAIN);
        assertThat(alert.getSource()).isEqualTo(AlertSource.SENSOR);
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.NEW);
        assertThat(alert.getOrchardId()).isEqualTo(inside.getId());
        assertThat(alert.getOpenAlertKey()).isEqualTo(inside.getId() + ":RAIN");
    }

    @Test
    void twoStationsOfADelegationAreNotAddedUp() {
        mongoTemplate.save(orchard("inside", "SAKIET_EZZIT", 0));
        rain("SFX-01", "SAKIET_EZZIT", Instant.now().minus(Duration.ofHours(2)), 12);
        rain("SFX-02", "SAKIET_EZZIT", Instant.now().minus(Duration.ofHours(2)), 12);

        autoAlertService.evaluate();

        assertThat(alertRepository.findAll()).isEmpty();   // 24 mm added up, but each station saw 12 mm
    }

    @Test
    void rainBelowThresholdOrOlderThan24HoursRaisesNothing() {
        mongoTemplate.save(orchard("inside", "SAKIET_EZZIT", 0));
        rain("SFX-01", "SAKIET_EZZIT", Instant.now().minus(Duration.ofHours(2)), 19);
        rain("SFX-01", "SAKIET_EZZIT", Instant.now().minus(Duration.ofHours(30)), 50);

        autoAlertService.evaluate();

        assertThat(alertRepository.findAll()).isEmpty();
    }

    @Test
    void maturityInOptimalWindowRaisesOneAlertAndANewOneOnlyAfterTheFirstIsClosed() {
        mongoTemplate.save(orchard("ripe", "SAKIET_EZZIT", 3.5)); // CHEMLALI window is [3.0, 4.5]
        mongoTemplate.save(orchard("green", "SAKIET_EZZIT", 1.0));

        autoAlertService.evaluate();
        autoAlertService.evaluate();

        List<FieldAlert> alerts = alertRepository.findAll();
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getType()).isEqualTo(AlertType.MATURITY);
        assertThat(alerts.get(0).getOrchardName()).isEqualTo("ripe");

        FieldAlert closed = alerts.get(0);
        closed.setStatus(AlertStatus.RESOLVED);
        assertThat(closed.getOpenAlertKey()).isNull();
        alertRepository.save(closed);
        autoAlertService.evaluate();
        assertThat(alertRepository.findAll()).hasSize(2);
    }

    private void rain(String station, String delegation, Instant at, double mm) {
        mongoTemplate.save(new WeatherReading(null, at, new WeatherReading.Meta(station, delegation), 14, 80, mm, 10));
    }

    private Orchard orchard(String name, String delegation, double maturity) {
        Orchard orchard = new Orchard();
        orchard.setName(name);
        orchard.setOwnerId("owner-1");
        orchard.setLocation(new GeoPoint(10.76, 34.74));
        orchard.setDelegation(delegation);
        Plot plot = new Plot();
        plot.setCode("P1");
        plot.setVarietyCode("CHEMLALI");
        plot.setAreaHa(1);
        plot.setTreeCount(100);
        orchard.setPlots(new ArrayList<>(List.of(plot)));
        orchard.recomputeTotals();
        orchard.setCurrentMaturityIndex(maturity);
        return orchard;
    }
}
