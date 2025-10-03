package org.github.tess1o.geopulse.digest.service.calculation;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.digest.model.PeriodComparison;
import org.github.tess1o.geopulse.statistics.model.UserStatistics;

/**
 * Calculates comparisons between current and previous periods.
 */
@ApplicationScoped
public class DigestComparisonCalculator {

    /**
     * Build comparison data between current and previous period.
     *
     * @param current  Current period statistics
     * @param previous Previous period statistics
     * @return Period comparison data
     */
    public PeriodComparison buildComparison(UserStatistics current, UserStatistics previous) {
        double currentDistance = current.getTotalDistanceMeters();
        double previousDistance = previous.getTotalDistanceMeters();

        double percentChange = 0;
        String direction = "same";

        if (previousDistance > 0) {
            percentChange = ((currentDistance - previousDistance) / previousDistance) * 100;
            if (percentChange > 0.5) {
                direction = "increase";
            } else if (percentChange < -0.5) {
                direction = "decrease";
            }
        } else if (currentDistance > 0) {
            direction = "increase";
            percentChange = 100;
        }

        return PeriodComparison.builder()
                .totalDistance(previousDistance)
                .percentChange(Math.round(percentChange * 10.0) / 10.0) // Round to 1 decimal
                .direction(direction)
                .activeDays(0) // Could be calculated from previous timeline if needed
                .activeDaysChange(0)
                .build();
    }
}
