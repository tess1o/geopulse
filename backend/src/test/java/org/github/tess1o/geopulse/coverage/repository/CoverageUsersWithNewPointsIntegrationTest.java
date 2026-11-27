package org.github.tess1o.geopulse.coverage.repository;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.coverage.CoverageDefaults;
import org.github.tess1o.geopulse.coverage.model.CoverageProcessingCursor;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.testsupport.TimelineSeeder;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Which users the scheduled coverage job picks up: enabled, active, idle (or stale) users with unprocessed points. */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
class CoverageUsersWithNewPointsIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-04-01T10:00:00Z");

    @Inject
    CoverageRepository coverageRepository;

    @Inject
    TimelineSeeder seed;

    @Inject
    EntityManager entityManager;

    @Test
    void picksUsersWithPointsAfterTheirCursorOnly() {
        UUID neverProcessed = enabledUserWithPoints(true);
        UUID upToDate = enabledUserWithPoints(true);
        processedUpToLastPoint(upToDate);
        UUID newerPoint = enabledUserWithPoints(true);
        processedUpToLastPoint(newerPoint);
        addPoint(newerPoint, T0.plusSeconds(3_600), 5.0);
        UUID sameTimestampHigherId = enabledUserWithPoints(true);
        processedUpToLastPoint(sameTimestampHigherId);
        addPoint(sameTimestampHigherId, T0.plusSeconds(20), 5.0);
        UUID onlyInaccurateNewPoint = enabledUserWithPoints(true);
        processedUpToLastPoint(onlyInaccurateNewPoint);
        addPoint(onlyInaccurateNewPoint, T0.plusSeconds(3_600), CoverageDefaults.MAX_ACCURACY_METERS + 1);
        UUID resetCursor = enabledUserWithPoints(true);
        processedUpToLastPoint(resetCursor);
        QuarkusTransaction.requiringNew().run(() -> coverageRepository.resetProcessingCursor(resetCursor));

        List<UUID> found = find();

        assertThat(found).contains(neverProcessed, newerPoint, sameTimestampHigherId, resetCursor);
        assertThat(found).doesNotContain(upToDate, onlyInaccurateNewPoint);
        assertThat(found).doesNotHaveDuplicates();
    }

    @Test
    void skipsDisabledInactiveAndBusyUsers() {
        UUID disabled = enabledUserWithPoints(false);
        UUID inactive = enabledUserWithPoints(true);
        QuarkusTransaction.requiringNew().run(() -> entityManager.find(UserEntity.class, inactive).setActive(false));
        UUID busy = enabledUserWithPoints(true);
        QuarkusTransaction.requiringNew().run(() -> coverageRepository.tryStartProcessing(busy, 60));
        UUID stale = enabledUserWithPoints(true);
        QuarkusTransaction.requiringNew().run(() -> {
            coverageRepository.tryStartProcessing(stale, 60);
            entityManager.createNativeQuery("UPDATE coverage_state SET processing_started_at = NOW() - INTERVAL '2 hours' " +
                            "WHERE user_id = :userId")
                    .setParameter("userId", stale)
                    .executeUpdate();
        });
        UUID withoutPoints = seed.user("coverage-new-none", "UTC").getId();
        QuarkusTransaction.requiringNew().run(() -> entityManager.find(UserEntity.class, withoutPoints).setCoverageEnabled(true));

        List<UUID> found = find();

        assertThat(found).contains(stale);
        assertThat(found).doesNotContain(disabled, inactive, busy, withoutPoints);
    }

    private List<UUID> find() {
        return QuarkusTransaction.requiringNew().call(() -> coverageRepository.findUsersWithNewCoverage(
                CoverageDefaults.MAX_ACCURACY_METERS, CoverageDefaults.PROCESSING_STALE_TIMEOUT_SECONDS));
    }

    private UUID enabledUserWithPoints(boolean coverageEnabled) {
        UUID userId = seed.user("coverage-new-points", "UTC").getId();
        QuarkusTransaction.requiringNew().run(() ->
                entityManager.find(UserEntity.class, userId).setCoverageEnabled(coverageEnabled));
        addPoint(userId, T0, 5.0);
        addPoint(userId, T0.plusSeconds(10), null);
        addPoint(userId, T0.plusSeconds(20), 5.0);
        return userId;
    }

    /** Each point a little further east: points with the same user, time and place are rejected as duplicates. */
    private void addPoint(UUID userId, Instant timestamp, Double accuracy) {
        QuarkusTransaction.requiringNew().run(() -> {
            long existing = entityManager.createQuery("SELECT COUNT(p) FROM GpsPointEntity p WHERE p.user.id = :userId",
                            Long.class)
                    .setParameter("userId", userId)
                    .getSingleResult();
            seed.gpsPoint(entityManager.find(UserEntity.class, userId), timestamp, 50.45, 30.52 + existing * 0.0001,
                    accuracy);
        });
    }

    private void processedUpToLastPoint(UUID userId) {
        QuarkusTransaction.requiringNew().run(() -> {
            GpsPointEntity last = entityManager.createQuery(
                            "FROM GpsPointEntity WHERE user.id = :userId ORDER BY timestamp DESC, id DESC",
                            GpsPointEntity.class)
                    .setParameter("userId", userId)
                    .setMaxResults(1)
                    .getSingleResult();
            coverageRepository.upsertLastProcessed(userId,
                    new CoverageProcessingCursor(last.getTimestamp(), last.getId()));
        });
    }
}
