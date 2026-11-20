package tn.oliveconnect.harvest;

import org.springframework.stereotype.Component;

/**
 * Calculates the bounded score used to order the collection-manager queue.
 *
 * <p>The inputs are deliberately explicit so the formula stays explainable and can later accept
 * the open-maturity-alert flag from the alert module.</p>
 */
@Component
public class PriorityCalculator {

    private static final double URGENT_WEIGHT = 40.0;
    private static final double MATURITY_WEIGHT = 30.0;
    private static final double WAITING_WEIGHT = 20.0;
    private static final double ALERT_WEIGHT = 10.0;

    public double calculate(HarvestType type, double maturityIndex, double optimalMaturityMin,
                            double optimalMaturityMax, long waitingDays, boolean openMaturityAlert) {
        double urgency = type == HarvestType.URGENT ? URGENT_WEIGHT : 0.0;
        double maturityProximity = maturityProximity(maturityIndex, optimalMaturityMin, optimalMaturityMax);
        double waiting = clamp(waitingDays / 7.0, 0.0, 1.0);
        double alert = openMaturityAlert ? ALERT_WEIGHT : 0.0;
        return urgency + MATURITY_WEIGHT * maturityProximity
                + WAITING_WEIGHT * waiting + alert;
    }

    double maturityProximity(double maturityIndex, double optimalMaturityMin, double optimalMaturityMax) {
        double midpoint = (optimalMaturityMin + optimalMaturityMax) / 2.0;
        double halfWidth = (optimalMaturityMax - optimalMaturityMin) / 2.0;
        if (halfWidth <= 0) {
            return Double.compare(maturityIndex, midpoint) == 0 ? 1.0 : 0.0;
        }
        return clamp(1.0 - Math.abs(maturityIndex - midpoint) / halfWidth, 0.0, 1.0);
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
