package org.github.tess1o.geopulse.gps.model;

import java.time.Instant;

/**
 * Privacy-preserving GPS status snapshot for user-facing monitoring.
 */
public record GpsStatusDTO(
        Instant generatedAt,
        boolean hasGpsData,
        Instant latestGpsPointTimestamp,
        Long latestGpsPointEpochSeconds,
        Long latestGpsPointAgeSeconds,
        Long latestGpsPointAgeMinutes,
        Instant latestGpsPointReceivedAt,
        String latestSourceType,
        String latestDeviceId,
        long totalGpsPoints
) {
}
