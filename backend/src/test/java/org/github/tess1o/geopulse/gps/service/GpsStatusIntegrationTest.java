package org.github.tess1o.geopulse.gps.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsStatusDTO;
import org.github.tess1o.geopulse.testsupport.TimelineSeeder;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GPS freshness over seeded points: the latest point is found by timestamp, not by insertion order.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class GpsStatusIntegrationTest {

    @Inject
    GpsPointService gpsPointService;

    @Inject
    TimelineSeeder seed;

    private UUID ownerId;
    private UUID emptyId;
    private Instant latest;

    @BeforeEach
    @Transactional
    void setUp() {
        UserEntity owner = seed.user("gps-status-owner", "UTC");
        ownerId = owner.getId();
        latest = Instant.now().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.SECONDS);
        seed.gpsPoint(owner, latest, 50.45, 30.52, 5.0);
        seed.gpsPoint(owner, latest.minus(Duration.ofHours(2)), 50.45, 30.52, 5.0);
        seed.gpsPoint(owner, latest.minus(Duration.ofHours(1)), 50.45, 30.52, 5.0);

        UserEntity other = seed.user("gps-status-other", "UTC");
        seed.gpsPoint(other, Instant.now().minusSeconds(5), 48.85, 2.35, 5.0);
        emptyId = seed.user("gps-status-empty", "UTC").getId();
    }

    @Test
    void status_reportsTheLatestPointAndTotalCount() {
        GpsStatusDTO status = gpsPointService.getGpsStatus(ownerId);

        assertThat(status.hasGpsData()).isTrue();
        assertThat(status.latestGpsPointTimestamp()).isEqualTo(latest);
        assertThat(status.latestGpsPointEpochSeconds()).isEqualTo(latest.getEpochSecond());
        assertThat(status.latestGpsPointAgeMinutes()).isBetween(29L, 31L);
        assertThat(status.latestSourceType()).isEqualTo("OWNTRACKS");
        assertThat(status.latestDeviceId()).isEqualTo("seeded-device");
        assertThat(status.totalGpsPoints()).isEqualTo(3);
    }

    @Test
    void status_withoutPointsHasNoData() {
        GpsStatusDTO status = gpsPointService.getGpsStatus(emptyId);

        assertThat(status.hasGpsData()).isFalse();
        assertThat(status.latestGpsPointTimestamp()).isNull();
        assertThat(status.totalGpsPoints()).isZero();
    }
}
