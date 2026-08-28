package org.github.tess1o.geopulse.mapmatching;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingSource;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.events.TimelineDataChangedEvent;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.TimelineStatus;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class MapMatchingTimelineChangeObserverTransactionTest {

    @Inject
    Event<TimelineDataChangedEvent> timelineDataChangedEvent;

    @Inject
    EntityManager entityManager;

    private UUID userId;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("DELETE FROM map_matching_reconciliations").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM timeline_trip_path_matches").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM system_settings WHERE key LIKE 'map-matching.%'").executeUpdate();

            UserEntity user = UserEntity.builder()
                    .email("map-matching-event-" + UUID.randomUUID() + "@example.com")
                    .passwordHash("test")
                    .fullName("Map Matching Event Test")
                    .createdAt(Instant.now())
                    .isActive(true)
                    .role(Role.USER)
                    .timezone("UTC")
                    .timelineStatus(TimelineStatus.IDLE)
                    .build();
            entityManager.persist(user);
            entityManager.flush();
            userId = user.getId();

            insertSetting("map-matching.enabled", "true", "BOOLEAN");
            insertSetting("map-matching.automatic.enabled", "true", "BOOLEAN");
            insertSetting("map-matching.automatic.quiet-period-minutes", "15", "INTEGER");
        });
    }

    @Test
    void timelineDataChangedAfterCommitQueuesAutomaticMapMatchingReconciliation() {
        Instant affectedFrom = Instant.parse("2026-08-01T00:00:00Z");
        Instant affectedTo = Instant.parse("2026-08-31T23:59:59Z");
        UUID jobId = UUID.randomUUID();

        QuarkusTransaction.requiringNew().run(() -> {
            UserEntity user = entityManager.find(UserEntity.class, userId);
            entityManager.persist(TimelineTripEntity.builder()
                    .user(user)
                    .timestamp(Instant.parse("2026-08-15T12:00:00Z"))
                    .tripDuration(600)
                    .distanceMeters(2_000)
                    .startPoint(GeoUtils.createPoint(30.5234, 50.4501))
                    .endPoint(GeoUtils.createPoint(30.5334, 50.4601))
                    .movementType("WALK")
                    .build());
            entityManager.flush();
            timelineDataChangedEvent.fire(new TimelineDataChangedEvent(userId, affectedFrom, affectedTo, jobId));
        });

        assertThat(awaitAutomaticReconciliationCount(userId)).isOne();
    }

    @Test
    void timelineDataChangedQueuesAutomaticRangeForBackdatedTripAfterHistoricalCursorPassed() {
        Instant backdatedTripStart = Instant.parse("2026-08-13T05:44:50Z");
        Instant affectedTo = Instant.parse("2026-08-28T20:07:01Z");
        UUID jobId = UUID.randomUUID();

        QuarkusTransaction.requiringNew().run(() -> {
            insertHistoricalReconciliationPast(backdatedTripStart);

            UserEntity user = entityManager.find(UserEntity.class, userId);
            entityManager.persist(TimelineTripEntity.builder()
                    .user(user)
                    .timestamp(backdatedTripStart)
                    .tripDuration(2_974)
                    .distanceMeters(14_829)
                    .startPoint(GeoUtils.createPoint(30.5234, 50.4501))
                    .endPoint(GeoUtils.createPoint(30.6334, 50.5601))
                    .movementType("CAR")
                    .build());
            entityManager.flush();
            timelineDataChangedEvent.fire(new TimelineDataChangedEvent(userId, backdatedTripStart, affectedTo, jobId));
        });

        assertThat(awaitAutomaticReconciliationCount(userId)).isOne();

        Object[] row = QuarkusTransaction.requiringNew().call(() -> (Object[]) entityManager.createNativeQuery("""
                SELECT range_start, range_end
                FROM map_matching_reconciliations
                WHERE user_id = ?1
                  AND source = 'AUTOMATIC'
                """)
                .setParameter(1, userId)
                .getSingleResult());

        assertThat(toInstant(row[0])).isEqualTo(backdatedTripStart);
        assertThat(toInstant(row[1])).isEqualTo(affectedTo);
    }

    private long awaitAutomaticReconciliationCount(UUID userId) {
        AssertionError lastFailure = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            long count = countAutomaticReconciliations(userId);
            try {
                assertThat(count).isOne();
                return count;
            } catch (AssertionError failure) {
                lastFailure = failure;
            }
            sleepQuietly();
        }
        throw lastFailure;
    }

    private long countAutomaticReconciliations(UUID userId) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM map_matching_reconciliations
                WHERE user_id = ?1
                  AND source = 'AUTOMATIC'
                """)
                .setParameter(1, userId)
                .getSingleResult()).longValue());
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

    private void insertHistoricalReconciliationPast(Instant backdatedTripStart) {
        entityManager.createNativeQuery("""
                INSERT INTO map_matching_reconciliations (
                    user_id, source, range_start, range_end, cursor_at, cursor_trip_id,
                    eligible_at, total_trips, scanned_trips, completed_at, created_at, updated_at
                )
                VALUES (?1, ?2, ?3, ?4, ?4, 10306, NOW(), 1602, 1602, NOW(), NOW(), NOW())
                """)
                .setParameter(1, userId)
                .setParameter(2, MapMatchingSource.HISTORICAL.name())
                .setParameter(3, backdatedTripStart.minusSeconds(3600))
                .setParameter(4, Instant.parse("2026-08-25T12:18:04Z"))
                .executeUpdate();
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

    private void sleepQuietly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for map matching reconciliation", e);
        }
    }
}
