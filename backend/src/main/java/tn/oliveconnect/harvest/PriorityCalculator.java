package tn.oliveconnect.harvest;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * Keeps the queue ordering in one place. The score is deliberately bounded to make it easy to
 * explain to collection managers: urgency (0 or 50), maturity (0 to 25), then date (0 to 2.5).
 */
@Component
public class PriorityCalculator {

    private static final double URGENT_SCORE = 50.0;
    private static final double MATURITY_SCORE_PER_POINT = 5.0;
    private static final double DATE_WINDOW_DAYS = 25.0;
    private static final double DATE_SCORE_PER_DAY = 0.1;

    private final Clock clock;

    public PriorityCalculator() {
        this(Clock.systemUTC());
    }

    PriorityCalculator(Clock clock) {
        this.clock = clock;
    }

    public double calculate(HarvestType type, double maturityIndex, LocalDate desiredFrom) {
        double urgency = type == HarvestType.URGENT ? URGENT_SCORE : 0.0;
        double maturity = clamp(maturityIndex, 0.0, 5.0) * MATURITY_SCORE_PER_POINT;
        long daysUntilHarvest = ChronoUnit.DAYS.between(LocalDate.now(clock), desiredFrom);
        double date = clamp(DATE_WINDOW_DAYS - daysUntilHarvest, 0.0, DATE_WINDOW_DAYS) * DATE_SCORE_PER_DAY;
        return urgency + maturity + date;
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
