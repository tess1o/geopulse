package org.github.tess1o.geopulse.mapmatching;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
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

    private void sleepQuietly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for map matching reconciliation", e);
        }
    }
}
