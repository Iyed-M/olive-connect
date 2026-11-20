package tn.oliveconnect.harvest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PriorityCalculatorTest {

    private final PriorityCalculator calculator = new PriorityCalculator();

    @Test
    void implementsTheDocumentedWeightedScore() {
        double score = calculator.calculate(HarvestType.URGENT, 3.75, 3.0, 4.5, 7, true);

        assertThat(score).isEqualTo(100.0);
    }

    @Test
    void maturityWaitingAndUrgencyIncreasePriority() {
        double urgent = calculator.calculate(HarvestType.URGENT, 3.75, 3.0, 4.5, 0, false);
        double mature = calculator.calculate(HarvestType.PLANNED, 3.75, 3.0, 4.5, 0, false);
        double lessMature = calculator.calculate(HarvestType.PLANNED, 2.0, 3.0, 4.5, 0, false);
        double waiting = calculator.calculate(HarvestType.PLANNED, 3.75, 3.0, 4.5, 7, false);

        assertThat(urgent).isGreaterThan(mature);
        assertThat(mature).isGreaterThan(lessMature);
        assertThat(waiting).isGreaterThan(mature);
    }
}
