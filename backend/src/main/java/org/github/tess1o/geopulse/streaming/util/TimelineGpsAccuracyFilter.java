package org.github.tess1o.geopulse.streaming.util;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;

/**
 * Shared GPS accuracy predicate for timeline generation and timeline map paths.
 */
public final class TimelineGpsAccuracyFilter {

    private TimelineGpsAccuracyFilter() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static boolean shouldInclude(GPSPoint point, TimelineConfig config) {
        if (point == null) {
            return false;
        }
        return shouldIncludeAccuracy(point.getAccuracy(), config);
    }

    public static boolean shouldIncludeAccuracy(Double accuracy, TimelineConfig config) {
        if (config == null || Boolean.FALSE.equals(config.getUseVelocityAccuracy())) {
            return true;
        }

        Double maxAccuracyThreshold = config.getStaypointMaxAccuracyThreshold();
        if (maxAccuracyThreshold == null || maxAccuracyThreshold <= 0) {
            return true;
        }

        return accuracy == null || accuracy <= maxAccuracyThreshold;
    }
}
