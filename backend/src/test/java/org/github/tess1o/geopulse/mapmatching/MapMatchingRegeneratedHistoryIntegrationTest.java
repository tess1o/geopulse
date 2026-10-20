package org.github.tess1o.geopulse.mapmatching;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingReconciliation;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingSource;
import org.github.tess1o.geopulse.mapmatching.repository.MapMatchingReconciliationRepository;
import org.github.tess1o.geopulse.mapmatching.repository.TimelineTripPathMatchRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.events.TimelineDataChangedEvent;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.testsupport.ExportTestFixtures;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.TimelineStatus;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regenerating a long range of history (an import, a full timeline recalculation) detaches every stored map match
 * and rewinds the user's automatic reconciliation. These tests cover how that backlog is rediscovered while the
 * user keeps tracking, so imported or recalculated days end up matched in the background instead of waiting for
 * someone to open them.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class MapMatchingRegeneratedHistoryIntegrationTest {

    private static final Duration AWAIT = Duration.ofSeconds(15);

    @Inject
    Event<TimelineDataChangedEvent> timelineDataChangedEvent;

    @Inject
    MapMatchingReconciliationRepository reconciliationRepository;

    @Inject
    TimelineTripPathMatchRepository targetRepository;

    @Inject
    EntityManager entityManager;

    private UUID userId;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            clearMapMatchingState();

            UserEntity user = UserEntity.builder()
                    .email("map-matching-history-" + UUID.randomUUID() + "@example.com")
                    .passwordHash("test")
                    .fullName("Map Matching History Test")
                    .createdAt(Instant.now())
                    .isActive(true)
                    .role(Role.USER)
                    .timezone("UTC")
                    .timelineStatus(TimelineStatus.IDLE)
                    .build();
            entityManager.persist(user);
            entityManager.flush();
            userId = user.getId();
        });
    }

    @AfterEach
    void tearDown() {
        QuarkusTransaction.requiringNew().run(this::clearMapMatchingState);
    }

    /**
     * An import regenerates the timeline from its first imported day up to now. While the user keeps moving, the
     * real-time job regenerates the latest trip every few minutes, and each of those changes must not hold back
     * matching of the imported days.
     */
    @Test
    void regeneratedHistoryIsDiscoveredWhileLiveTrackingKeepsChangingTheTimeline() {
        enableAutomaticMatching();
        Instant now = Instant.now();
        Instant importedFrom = now.minus(70, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        List<Long> importedTripIds = new ArrayList<>();
        for (int day = 0; day < 3; day++) {
            importedTripIds.add(persistTripWithPoints(
                    importedFrom.plus(day, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS), Duration.ofMinutes(20)));
        }

        fireTimelineChanged(importedFrom, now);
        awaitTrue("automatic reconciliation queued for the imported range",
                () -> automaticRangeEnd().filter(end -> !end.isBefore(now)).isPresent());

        // The quiet period after the import passes...
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
                        UPDATE map_matching_reconciliations
                        SET eligible_at = LEAST(eligible_at, now() - interval '1 minute')
                        WHERE user_id = ?1
                        """)
                .setParameter(1, userId)
                .executeUpdate());

        // ...but the user is on the move, so the real-time job keeps regenerating the trip in progress.
        Instant liveTripStart = Instant.now().minus(10, ChronoUnit.MINUTES);
        long liveTripId = persistTripWithPoints(liveTripStart, Duration.ofMinutes(8));
        Instant liveChangeTo = Instant.now();
        fireTimelineChanged(liveTripStart, liveChangeTo);
        awaitTrue("automatic reconciliation extended by the live change",
                () -> automaticRangeEnd().filter(end -> !end.isBefore(liveChangeTo)).isPresent());

        awaitTrue("imported trips discovered for background map matching",
                () -> importedTripIds.stream().allMatch(this::hasTarget));
        assertThat(hasTarget(liveTripId))
                .as("a trip that ended within the quiet period is still changing and must not be matched yet")
                .isFalse();
    }

    /**
     * Opening a day keeps working, but the backlog should not wait: a newer change must not postpone a
     * reconciliation that is already due.
     */
    @Test
    void laterTimelineChangeDoesNotPostponeDueBacklog() {
        Instant now = Instant.now();
        QuarkusTransaction.requiringNew().run(() -> reconciliationRepository.enqueue(userId,
                now.minus(70, ChronoUnit.DAYS), now, MapMatchingSource.AUTOMATIC, now.minus(1, ChronoUnit.MINUTES)));
        QuarkusTransaction.requiringNew().run(() -> reconciliationRepository.enqueue(userId,
                now.minus(10, ChronoUnit.MINUTES), Instant.now(), MapMatchingSource.AUTOMATIC,
                Instant.now().plus(15, ChronoUnit.MINUTES)));

        Optional<MapMatchingReconciliation> claimed = QuarkusTransaction.requiringNew()
                .call(() -> reconciliationRepository.claimNext(true, false));

        assertThat(claimed).as("the due backlog can still be scanned").isPresent();
    }

    /**
     * An import or recalculation that lands while the worker scans a chunk rewinds the cursor. Finishing that chunk
     * must not move the cursor forward again or mark the rewound range as done.
     */
    @Test
    void finishingChunkKeepsRangeQueuedWhileItWasScanned() {
        Instant recentStart = Instant.now().minus(3, ChronoUnit.DAYS);
        Instant recentEnd = Instant.now();
        QuarkusTransaction.requiringNew().run(() -> reconciliationRepository.enqueue(userId,
                recentStart, recentEnd, MapMatchingSource.AUTOMATIC, Instant.now().minus(1, ChronoUnit.MINUTES)));
        MapMatchingReconciliation claimed = QuarkusTransaction.requiringNew()
                .call(() -> reconciliationRepository.claimNext(true, false))
                .orElseThrow();

        Instant importedFrom = Instant.now().minus(70, ChronoUnit.DAYS);
        QuarkusTransaction.requiringNew().run(() -> reconciliationRepository.enqueue(userId,
                importedFrom, Instant.now(), MapMatchingSource.AUTOMATIC, Instant.now()));

        QuarkusTransaction.requiringNew().run(() -> reconciliationRepository.advance(
                claimed, recentEnd.minus(1, ChronoUnit.HOURS), 42L, 3, true));

        Object[] row = QuarkusTransaction.requiringNew().call(() -> (Object[]) entityManager.createNativeQuery("""
                        SELECT cursor_at, completed_at
                        FROM map_matching_reconciliations
                        WHERE user_id = ?1 AND source = 'AUTOMATIC'
                        """)
                .setParameter(1, userId)
                .getSingleResult());
        assertThat(row[1]).as("the imported range is still pending").isNull();
        assertThat(toInstant(row[0])).as("the cursor stays at the imported range")
                .isBeforeOrEqualTo(importedFrom);
    }

    /**
     * A recalculation detaches every stored match until the rescan reattaches it by its input hash. The nightly
     * cleanup must not delete those matches while that rescan is still pending, or old days have to be matched
     * through Valhalla again.
     */
    @Test
    void cleanupKeepsDetachedMatchesWhileRescanIsPending() {
        Instant matchedLongAgo = Instant.now().minus(120, ChronoUnit.DAYS);
        QuarkusTransaction.requiringNew().run(() -> {
            insertDetachedMatch(matchedLongAgo);
            reconciliationRepository.enqueue(userId, Instant.now().minus(200, ChronoUnit.DAYS), Instant.now(),
                    MapMatchingSource.AUTOMATIC, Instant.now());
        });

        long deletedWhilePending = QuarkusTransaction.requiringNew().call(() -> targetRepository.cleanupDetached(
                Instant.now().minus(30, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.HOURS)));

        assertThat(deletedWhilePending).isZero();
        assertThat(countTargets()).isOne();

        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
                        UPDATE map_matching_reconciliations SET completed_at = now() WHERE user_id = ?1
                        """)
                .setParameter(1, userId)
                .executeUpdate());

        long deletedAfterRescan = QuarkusTransaction.requiringNew().call(() -> targetRepository.cleanupDetached(
                Instant.now().minus(30, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.HOURS)));

        assertThat(deletedAfterRescan).as("a match nothing reattached after the rescan is a real orphan").isOne();
    }

    private void enableAutomaticMatching() {
        QuarkusTransaction.requiringNew().run(() -> {
            insertSetting("map-matching.enabled", "true", "BOOLEAN");
            insertSetting("map-matching.automatic.enabled", "true", "BOOLEAN");
            insertSetting("map-matching.automatic.quiet-period-minutes", "15", "INTEGER");
            // Nothing listens here: matching attempts fail fast and are retried, discovery is what is under test.
            insertSetting("map-matching.valhalla.base-url", "http://127.0.0.1:9", "STRING");
        });
    }

    private long persistTripWithPoints(Instant start, Duration duration) {
        return QuarkusTransaction.requiringNew().call(() -> {
            UserEntity user = entityManager.find(UserEntity.class, userId);
            TimelineTripEntity trip = TimelineTripEntity.builder()
                    .user(user)
                    .timestamp(start)
                    .tripDuration(duration.toSeconds())
                    .distanceMeters(3_000)
                    .startPoint(GeoUtils.createPoint(30.5234, 50.4501))
                    .endPoint(GeoUtils.createPoint(30.5534, 50.4701))
                    .movementType("CAR")
                    .build();
            entityManager.persist(trip);
            for (int i = 0; i <= 4; i++) {
                entityManager.persist(ExportTestFixtures.gpsPoint(user,
                        start.plus(duration.multipliedBy(i).dividedBy(4)),
                        50.4501 + i * 0.005, 30.5234 + i * 0.0075));
            }
            entityManager.flush();
            return trip.getId();
        });
    }

    private void fireTimelineChanged(Instant from, Instant to) {
        QuarkusTransaction.requiringNew().run(() -> timelineDataChangedEvent.fire(
                new TimelineDataChangedEvent(userId, from, to, UUID.randomUUID(), true)));
    }

    private Optional<Instant> automaticRangeEnd() {
        return QuarkusTransaction.requiringNew().call(() -> {
            List<?> rows = entityManager.createNativeQuery("""
                            SELECT range_end
                            FROM map_matching_reconciliations
                            WHERE user_id = ?1 AND source = 'AUTOMATIC'
                            """)
                    .setParameter(1, userId)
                    .getResultList();
            return rows.stream().findFirst().map(this::toInstant);
        });
    }

    private boolean hasTarget(long tripId) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager.createNativeQuery("""
                        SELECT count(*) FROM timeline_trip_path_matches WHERE trip_id = ?1
                        """)
                .setParameter(1, tripId)
                .getSingleResult()).longValue() > 0);
    }

    private long countTargets() {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager.createNativeQuery("""
                        SELECT count(*) FROM timeline_trip_path_matches WHERE user_id = ?1
                        """)
                .setParameter(1, userId)
                .getSingleResult()).longValue());
    }

    private void insertDetachedMatch(Instant completedAt) {
        entityManager.createNativeQuery("""
                        INSERT INTO timeline_trip_path_matches
                            (trip_id, user_id, provider, profile, config_hash, input_hash, status, attempts,
                             next_attempt_at, completed_at, matched_segments_json, source, priority,
                             created_at, updated_at)
                        VALUES (NULL, ?1, 'valhalla', 'auto', 'config', 'input', 'MATCHED', 1,
                                ?2, ?2, '[]', 'HISTORICAL', 10, ?2, ?2)
                        """)
                .setParameter(1, userId)
                .setParameter(2, completedAt)
                .executeUpdate();
    }

    private void insertSetting(String key, String value, String valueType) {
        entityManager.createNativeQuery("""
                        INSERT INTO system_settings (key, value, value_type, category, description, updated_at)
                        VALUES (?1, ?2, ?3, 'map-matching', 'Test map matching setting', NOW())
                        """)
                .setParameter(1, key)
                .setParameter(2, value)
                .setParameter(3, valueType)
                .executeUpdate();
    }

    private void clearMapMatchingState() {
        entityManager.createNativeQuery("DELETE FROM system_settings WHERE key LIKE 'map-matching.%'").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM map_matching_reconciliations").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM timeline_trip_path_matches").executeUpdate();
    }

    private void awaitTrue(String description, BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(AWAIT);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timed out waiting for: " + description);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for: " + description, e);
            }
        }
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return null;
    }
}
