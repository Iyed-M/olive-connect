package tn.oliveconnect.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import tn.oliveconnect.AbstractIntegrationTest;
import tn.oliveconnect.dashboard.dto.FillRatePoint;
import tn.oliveconnect.dashboard.dto.KpiFilter;
import tn.oliveconnect.dashboard.dto.LeadTimePoint;
import tn.oliveconnect.dashboard.dto.MillVolumePoint;
import tn.oliveconnect.dashboard.dto.YieldPoint;
import tn.oliveconnect.mill.MillReception;
import tn.oliveconnect.mill.QualityAssessment;
import tn.oliveconnect.mill.QualityGrade;

class KpiRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    KpiRepositoryCustom kpiRepository;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), MillReception.class);
    }

    @Test
    void fillRateByWeekMatchesTheHandComputedFixture() {
        saveReception("2026-11-16T10:00:00Z", "2026-2027", 2_000, 4_000);
        saveReception("2026-11-18T10:00:00Z", "2026-2027", 3_600, 4_000);
        saveReception("2026-11-18T10:00:00Z", "2025-2026", 4_000, 4_000);

        List<FillRatePoint> points = kpiRepository.aggregateFillRate(
                new KpiFilter("2026-2027", null, null, null, null, null), "week");

        assertThat(points).singleElement().satisfies(point -> {
            assertThat(point.year()).isEqualTo(2026);
            assertThat(point.week()).isEqualTo(47);
            assertThat(point.avgFillRate()).isEqualTo(0.7);
            assertThat(point.receptions()).isEqualTo(2);
            assertThat(point.underFilled()).isEqualTo(1);
        });
    }

    @Test
    void yieldLeadTimeAndMillVolumeUseTheReceptionDimensions() {
        MillReception first = saveReception("2026-11-16T10:00:00Z", "2026-2027", 2_000, 4_000);
        first.setLeadTimeHours(24);
        mongoTemplate.save(first);
        MillReception second = saveReception("2026-11-16T11:00:00Z", "2026-2027", 3_600, 4_000);
        second.setOrchardId("orchard-2");
        second.setDelegation("THYNA");
        second.setLeadTimeHours(48);
        mongoTemplate.save(second);

        KpiFilter filter = new KpiFilter("2026-2027", null, null, null, null, "mill-1");
        List<YieldPoint> yields = kpiRepository.aggregateYield(filter, "orchard");
        List<LeadTimePoint> leadTimes = kpiRepository.aggregateLeadTime(filter, "delegation");
        List<MillVolumePoint> volumes = kpiRepository.aggregateMillVolumes(filter);

        assertThat(yields).hasSize(2);
        assertThat(yields).extracting(YieldPoint::totalNetKg).containsExactlyInAnyOrder(2_000.0, 3_600.0);
        assertThat(leadTimes).hasSize(2);
        assertThat(leadTimes).extracting(LeadTimePoint::n).containsOnly(1L);
        assertThat(volumes).singleElement().satisfies(volume -> {
            assertThat(volume.millId()).isEqualTo("mill-1");
            assertThat(volume.totalNetKg()).isEqualTo(5_600);
            assertThat(volume.receptions()).isEqualTo(2);
            assertThat(volume.qualityDistribution()).containsEntry("GOOD", 2L);
        });
    }

    private MillReception saveReception(String receivedAt, String season, double netWeightKg, double capacityKg) {
        MillReception reception = new MillReception();
        reception.setSeason(season);
        reception.setGrossWeightKg(netWeightKg);
        reception.setTareKg(0);
        reception.setBinCapacityKg(capacityKg);
        reception.computeNetAndFillRate();
        reception.setReceivedAt(Instant.parse(receivedAt));
        reception.setRoundId(UUID.randomUUID().toString());
        reception.setBinId(UUID.randomUUID().toString());
        reception.setOrchardId(UUID.randomUUID().toString());
        reception.setMillId("mill-1");
        reception.setVarietyCode("CHEMLALI");
        reception.setDelegation("SAKIET_EZZIT");
        reception.setOrchardAreaHa(2);
        reception.setLeadTimeHours(12);
        QualityAssessment quality = new QualityAssessment();
        quality.setGrade(QualityGrade.GOOD);
        quality.setEstimatedOilYieldPct(20);
        reception.setQuality(quality);
        mongoTemplate.save(reception);
        return reception;
    }
}
