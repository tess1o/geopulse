package org.github.tess1o.geopulse.streaming.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TimelineRebuildRequestTest {

    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    @Test
    void requestIsRetriedOnEveryPassUntilItHasFailedEnoughTimes() {
        assertThat(request(0, null).isParked(NOW)).isFalse();
        assertThat(request(TimelineRebuildRequest.MAX_FAILED_ATTEMPTS - 1, NOW.minusSeconds(10)).isParked(NOW))
                .isFalse();
    }

    @Test
    void requestThatKeepsFailingWaitsForItsRetry() {
        Instant justFailed = NOW.minusSeconds(60);
        assertThat(request(TimelineRebuildRequest.MAX_FAILED_ATTEMPTS, justFailed).isParked(NOW)).isTrue();
    }

    @Test
    void parkedRequestIsRetriedOnceTheDelayHasPassed() {
        Instant longAgo = NOW.minus(TimelineRebuildRequest.RETRY_FAILED_AFTER).minusSeconds(1);
        assertThat(request(TimelineRebuildRequest.MAX_FAILED_ATTEMPTS + 5, longAgo).isParked(NOW)).isFalse();
    }

    private static TimelineRebuildRequest request(int failedAttempts, Instant lastFailedAt) {
        return new TimelineRebuildRequest(UUID.randomUUID(), NOW.minusSeconds(3600), 1, failedAttempts, lastFailedAt);
    }
}
