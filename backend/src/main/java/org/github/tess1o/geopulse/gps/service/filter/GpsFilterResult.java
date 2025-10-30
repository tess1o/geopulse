package org.github.tess1o.geopulse.gps.service.filter;

import lombok.Getter;
import lombok.ToString;

/**
 * Result of GPS data filtering operation.
 * Indicates whether a GPS point was accepted or rejected, and if rejected, why.
 */
@Getter
@ToString
public class GpsFilterResult {
    private final boolean accepted;
    private final String rejectionReason; // null if accepted

    private GpsFilterResult(boolean accepted, String rejectionReason) {
        this.accepted = accepted;
        this.rejectionReason = rejectionReason;
    }

    /**
     * Create a result indicating the GPS point was accepted
     */
    public static GpsFilterResult accepted() {
        return new GpsFilterResult(true, null);
    }

    /**
     * Create a result indicating the GPS point was rejected due to poor accuracy
     *
     * @param actual    The actual accuracy value in meters
     * @param threshold The maximum allowed accuracy in meters
     */
    public static GpsFilterResult rejectedByAccuracy(double actual, int threshold) {
        String reason = String.format("Accuracy %.1fm exceeds threshold %dm", actual, threshold);
        return new GpsFilterResult(false, reason);
    }

    /**
     * Create a result indicating the GPS point was rejected due to excessive speed
     *
     * @param actual    The actual speed value in km/h
     * @param threshold The maximum allowed speed in km/h
     */
    public static GpsFilterResult rejectedBySpeed(double actual, int threshold) {
        String reason = String.format("Speed %.1f km/h exceeds threshold %d km/h", actual, threshold);
        return new GpsFilterResult(false, reason);
    }

    /**
     * Check if the GPS point was rejected
     */
    public boolean isRejected() {
        return !accepted;
    }
}
