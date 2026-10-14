package org.github.tess1o.geopulse.gpssource.model;

import java.time.Instant;
import java.util.Map;

/**
 * Latest ingest activity of one GPS source. All fields are null until the source receives its first request.
 *
 * @param lastReceivedAt          when the last authenticated ingest request arrived
 * @param lastSavedAt             when a point from this source was last stored
 * @param lastSavedPointTimestamp GPS time of the newest stored point (differs from {@code lastSavedAt} for
 *                                delayed or queued uploads)
 * @param lastRejectedAt          when a point was last skipped
 * @param lastRejectionCode       why it was skipped, a {@code GpsIngestRejectionCode} name
 * @param lastRejectionParams     values explaining the skip, e.g. {@code accuracy} and {@code threshold}
 * @param lastAuthFailureAt       when a request for this source was last refused
 * @param lastAuthFailureCode     why it was refused, a {@link GpsSourceAuthFailureCode} name
 */
public record GpsSourceIngestStatusDTO(
        Instant lastReceivedAt,
        Instant lastSavedAt,
        Instant lastSavedPointTimestamp,
        Instant lastRejectedAt,
        String lastRejectionCode,
        Map<String, Object> lastRejectionParams,
        Instant lastAuthFailureAt,
        String lastAuthFailureCode
) {
}
