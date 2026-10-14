package org.github.tess1o.geopulse.gps.service.filter;

import lombok.Getter;
import lombok.ToString;
import org.github.tess1o.geopulse.gps.service.GpsIngestRejection;
import org.github.tess1o.geopulse.gps.service.GpsIngestRejectionCode;

import java.util.Map;

/**
 * Result of GPS data filtering operation.
 * Indicates whether a GPS point was accepted or rejected, and if rejected, why.
 */
@Getter
@ToString
public class GpsFilterResult {
    private final boolean accepted;
    private final String rejectionReason; // null if accepted
    private final GpsIngestRejection rejection; // null if accepted

    private GpsFilterResult(boolean accepted, String rejectionReason, GpsIngestRejection rejection) {
        this.accepted = accepted;
        this.rejectionReason = rejectionReason;
        this.rejection = rejection;
    }

    /**
     * Create a result indicating the GPS point was accepted
     */
    public static GpsFilterResult accepted() {
        return new GpsFilterResult(true, null, null);
    }

    /**
     * Create a result indicating the GPS point was rejected because accuracy is absent
     * and a maximum accuracy threshold is configured.
     *
     * @param threshold The maximum allowed accuracy in meters
     */
    public static GpsFilterResult rejectedByMissingAccuracy(int threshold) {
        String reason = String.format("No accuracy value provided; threshold is %dm", threshold);
        return new GpsFilterResult(false, reason, new GpsIngestRejection(GpsIngestRejectionCode.MISSING_ACCURACY,
                Map.of("threshold", threshold)));
    }

    /**
     * Create a result indicating the GPS point was rejected due to poor accuracy
     *
     * @param actual    The actual accuracy value in meters
     * @param threshold The maximum allowed accuracy in meters
     */
    public static GpsFilterResult rejectedByAccuracy(double actual, int threshold) {
        String reason = String.format("Accuracy %.1fm exceeds threshold %dm", actual, threshold);
        return new GpsFilterResult(false, reason, new GpsIngestRejection(GpsIngestRejectionCode.ACCURACY_EXCEEDED,
                Map.of("accuracy", roundToTenth(actual), "threshold", threshold)));
    }

    /**
     * Create a result indicating the GPS point was rejected due to invalid negative accuracy.
     *
     * @param actual The actual accuracy value in meters
     */
    public static GpsFilterResult rejectedByNegativeAccuracy(double actual) {
        String reason = String.format("Accuracy %.1fm is invalid (negative values are not allowed)", actual);
        return new GpsFilterResult(false, reason, new GpsIngestRejection(GpsIngestRejectionCode.NEGATIVE_ACCURACY,
                Map.of("accuracy", roundToTenth(actual))));
    }

    /**
     * Create a result indicating the GPS point was rejected due to excessive speed
     *
     * @param actual    The actual speed value in km/h
     * @param threshold The maximum allowed speed in km/h
     */
    public static GpsFilterResult rejectedBySpeed(double actual, int threshold) {
        String reason = String.format("Speed %.1f km/h exceeds threshold %d km/h", actual, threshold);
        return new GpsFilterResult(false, reason, new GpsIngestRejection(GpsIngestRejectionCode.SPEED_EXCEEDED,
                Map.of("speed", roundToTenth(actual), "threshold", threshold)));
    }

    /**
     * Check if the GPS point was rejected
     */
    public boolean isRejected() {
        return !accepted;
    }

    private static double roundToTenth(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
