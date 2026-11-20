package tn.oliveconnect.harvest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PriorityCalculatorTest {

    private final PriorityCalculator calculator = new PriorityCalculator(
            Clock.fixed(Instant.parse("2026-11-20T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void urgentRequestsRankAbovePlannedRequests() {
        double urgent = calculator.calculate(HarvestType.URGENT, 0, LocalDate.of(2026, 11, 27));
        double planned = calculator.calculate(HarvestType.PLANNED, 5, LocalDate.of(2026, 11, 20));

        assertThat(urgent).isGreaterThan(planned);
    }

    @Test
    void maturityAndEarlierDesiredDatesIncreasePriority() {
        double mature = calculator.calculate(HarvestType.PLANNED, 4.0, LocalDate.of(2026, 11, 25));
        double lessMature = calculator.calculate(HarvestType.PLANNED, 2.0, LocalDate.of(2026, 11, 25));
        double earlier = calculator.calculate(HarvestType.PLANNED, 2.0, LocalDate.of(2026, 11, 22));
        double later = calculator.calculate(HarvestType.PLANNED, 2.0, LocalDate.of(2026, 12, 10));

        assertThat(mature).isGreaterThan(lessMature);
        assertThat(earlier).isGreaterThan(later);
    }
}
