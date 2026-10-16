package org.github.tess1o.geopulse.streaming.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A pending timeline rebuild: the user's timeline is out of date from {@code rebuildFrom} onwards.
 *
 * @param revision       changes on every write; a generation clears only the revision it read
 * @param failedAttempts consecutive failed generations of this request
 */
public record TimelineRebuildRequest(UUID userId,
                                     Instant rebuildFrom,
                                     long revision,
                                     int failedAttempts,
                                     Instant lastFailedAt) {

    /** Failed generations after which the real-time job stops retrying the request on every pass. */
    public static final int MAX_FAILED_ATTEMPTS = 3;

    /** How long a request that keeps failing waits before the real-time job tries it again. */
    public static final Duration RETRY_FAILED_AFTER = Duration.ofHours(1);

    /**
     * True while the request keeps failing and is waiting for its next retry. The real-time job then rebuilds only
     * the latest stay, so one broken range cannot stop the rest of the timeline from updating.
     */
    public boolean isParked(Instant now) {
        return failedAttempts >= MAX_FAILED_ATTEMPTS
                && lastFailedAt != null
                && lastFailedAt.plus(RETRY_FAILED_AFTER).isAfter(now);
    }
}
