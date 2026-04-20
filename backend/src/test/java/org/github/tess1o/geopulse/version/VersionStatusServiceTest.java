package org.github.tess1o.geopulse.version;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class VersionStatusServiceTest {

    @Test
    void getVersionStatus_UsesFreshCache_WhenTtlNotExpired() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-20T10:00:00Z"));
        QueueReleaseFetcher fetcher = new QueueReleaseFetcher();
        fetcher.enqueueSuccess(new GitHubReleaseInfo(
                "v1.28.0",
                "https://github.com/tess1o/geopulse/releases/tag/v1.28.0",
                Instant.parse("2026-04-19T12:00:00Z")
        ));

        VersionStatusService service = new VersionStatusService(
                "1.27.0",
                clock,
                Duration.ofMinutes(60),
                "https://github.com/tess1o/geopulse/releases",
                fetcher
        );

        VersionStatusResponse first = service.getVersionStatus();
        VersionStatusResponse second = service.getVersionStatus();

        assertEquals("fresh", first.sourceStatus());
        assertEquals("fresh", second.sourceStatus());
        assertEquals("1.28.0", first.latestVersion());
        assertTrue(first.updateAvailable());
        assertEquals(1, fetcher.invocationCount);
    }

    @Test
    void getVersionStatus_ReturnsStale_WhenRefreshFailsButCacheExists() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-20T10:00:00Z"));
        QueueReleaseFetcher fetcher = new QueueReleaseFetcher();
        fetcher.enqueueSuccess(new GitHubReleaseInfo(
                "v1.28.0",
                "https://github.com/tess1o/geopulse/releases/tag/v1.28.0",
                Instant.parse("2026-04-19T12:00:00Z")
        ));
        fetcher.enqueueFailure(new RuntimeException("network down"));

        VersionStatusService service = new VersionStatusService(
                "1.27.0",
                clock,
                Duration.ofMinutes(1),
                "https://github.com/tess1o/geopulse/releases",
                fetcher
        );

        VersionStatusResponse fresh = service.getVersionStatus();
        clock.advance(Duration.ofMinutes(2));
        VersionStatusResponse stale = service.getVersionStatus();

        assertEquals("fresh", fresh.sourceStatus());
        assertEquals("stale", stale.sourceStatus());
        assertEquals("1.28.0", stale.latestVersion());
        assertTrue(stale.updateAvailable());
        assertEquals(2, fetcher.invocationCount);
    }

    @Test
    void getVersionStatus_ReturnsUnavailable_WhenNoCacheAndFetchFails() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-20T10:00:00Z"));
        QueueReleaseFetcher fetcher = new QueueReleaseFetcher();
        fetcher.enqueueFailure(new RuntimeException("GitHub unavailable"));

        VersionStatusService service = new VersionStatusService(
                "1.27.0",
                clock,
                Duration.ofMinutes(60),
                "https://github.com/tess1o/geopulse/releases",
                fetcher
        );

        VersionStatusResponse response = service.getVersionStatus();

        assertEquals("unavailable", response.sourceStatus());
        assertEquals("1.27.0", response.currentVersion());
        assertNull(response.latestVersion());
        assertFalse(response.updateAvailable());
    }

    @Test
    void getVersionStatus_DisablesUpdate_WhenCurrentVersionMalformed() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-20T10:00:00Z"));
        QueueReleaseFetcher fetcher = new QueueReleaseFetcher();
        fetcher.enqueueSuccess(new GitHubReleaseInfo(
                "v1.28.0",
                "https://github.com/tess1o/geopulse/releases/tag/v1.28.0",
                Instant.parse("2026-04-19T12:00:00Z")
        ));

        VersionStatusService service = new VersionStatusService(
                "1.27.0-SNAPSHOT",
                clock,
                Duration.ofMinutes(60),
                "https://github.com/tess1o/geopulse/releases",
                fetcher
        );

        VersionStatusResponse response = service.getVersionStatus();

        assertEquals("1.28.0", response.latestVersion());
        assertFalse(response.updateAvailable());
    }

    @Test
    void compareVersions_HandlesAdditionalSegments() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-20T10:00:00Z"));
        VersionStatusService service = new VersionStatusService(
                "1.27.0",
                clock,
                Duration.ofMinutes(60),
                "https://github.com/tess1o/geopulse/releases",
                () -> new GitHubReleaseInfo("v1.27.0", null, null)
        );

        assertEquals(0, service.compareVersions("1.27", "1.27.0"));
        assertTrue(service.compareVersions("1.27.1", "1.27") > 0);
        assertTrue(service.compareVersions("1.27.0", "1.27.1") < 0);
    }

    private static final class QueueReleaseFetcher implements ReleaseFetcher {
        private final Deque<Object> queue = new ArrayDeque<>();
        private int invocationCount = 0;

        private void enqueueSuccess(GitHubReleaseInfo releaseInfo) {
            queue.addLast(releaseInfo);
        }

        private void enqueueFailure(Exception exception) {
            queue.addLast(exception);
        }

        @Override
        public GitHubReleaseInfo fetchLatestRelease() throws Exception {
            invocationCount++;

            Object next = queue.pollFirst();
            if (next instanceof GitHubReleaseInfo releaseInfo) {
                return releaseInfo;
            }

            if (next instanceof Exception exception) {
                throw exception;
            }

            throw new IllegalStateException("No queued fetch result");
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
