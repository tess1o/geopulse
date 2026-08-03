package org.github.tess1o.geopulse.weather.job;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.events.TimelineDataChangedEvent;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.TimelineStatus;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.weather.event.WeatherSettingsChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class WeatherHistoricalReconciliationEventIntegrationTest {

    @Inject
    Event<TimelineDataChangedEvent> timelineDataChangedEvent;

    @Inject
    Event<WeatherSettingsChangedEvent> weatherSettingsChangedEvent;

    @Inject
    EntityManager entityManager;

    @Inject
    WeatherHistoricalReconciliationJob reconciliationJob;

    private UUID userId;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("DELETE FROM weather_backfill_reconciliations").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM weather_sample_targets").executeUpdate();
            setBooleanSetting("weather.enabled", true);
            setBooleanSetting("weather.backfill.enabled", true);
            setSetting("weather.quota.daily-request-limit", "0", "INTEGER");

            UserEntity user = UserEntity.builder()
                    .email("weather-event-" + UUID.randomUUID() + "@example.com")
                    .passwordHash("test")
                    .fullName("Weather Event Test")
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
    void waitForEventTriggeredProcessing() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (reconciliationJob.hasSubmittedProcessingTasks() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        assertThat(reconciliationJob.hasSubmittedProcessingTasks()).isFalse();
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
                DELETE FROM system_settings
                WHERE key IN (
                    'weather.enabled',
                    'weather.backfill.enabled',
                    'weather.quota.daily-request-limit'
                )
                """).executeUpdate());
    }

    @Test
    void timelineChangeAfterCommitPersistsReconciliationRange() {
        Instant affectedTo = Instant.now();
        Instant affectedFrom = affectedTo.minus(Duration.ofMinutes(30));

        QuarkusTransaction.requiringNew().run(() -> timelineDataChangedEvent.fire(
                new TimelineDataChangedEvent(userId, affectedFrom, affectedTo, UUID.randomUUID())));

        Object[] queuedRange = QuarkusTransaction.requiringNew().call(() ->
                (Object[]) entityManager.createNativeQuery("""
                        SELECT range_start, range_end
                        FROM weather_backfill_reconciliations
                        WHERE user_id = ?1
                        """)
                        .setParameter(1, userId)
                        .getSingleResult());

        assertThat(queuedRange[0]).isEqualTo(affectedFrom);
        assertThat(queuedRange[1]).isEqualTo(affectedTo);
    }

    @Test
    void weatherSettingChangeAfterCommitQueuesFullReconciliation() {
        Instant timelineStart = Instant.now().minus(Duration.ofDays(730));
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
                INSERT INTO timeline_stays (
                    user_id, timestamp, stay_duration, location, location_name,
                    created_at, last_updated, location_source
                )
                VALUES (?1, ?2, 0, ST_SetSRID(ST_MakePoint(30.52, 50.45), 4326),
                        'Integration test stay', NOW(), NOW(), 'GEOCODING')
                """)
                .setParameter(1, userId)
                .setParameter(2, timelineStart)
                .executeUpdate());

        QuarkusTransaction.requiringNew().run(() -> weatherSettingsChangedEvent.fire(
                new WeatherSettingsChangedEvent("weather.coordinate-precision")));

        long queuedUsers = QuarkusTransaction.requiringNew().call(() ->
                ((Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM weather_backfill_reconciliations
                        WHERE user_id = ?1
                        """)
                        .setParameter(1, userId)
                        .getSingleResult()).longValue());

        assertThat(queuedUsers).isOne();
    }

    private void setBooleanSetting(String key, boolean value) {
        setSetting(key, Boolean.toString(value), "BOOLEAN");
    }

    private void setSetting(String key, String value, String valueType) {
        entityManager.createNativeQuery("""
                INSERT INTO system_settings (key, value, value_type, category, description, updated_at)
                VALUES (?1, ?2, ?3, 'weather', 'Integration test override', NOW())
                ON CONFLICT (key) DO UPDATE
                SET value = EXCLUDED.value,
                    updated_at = NOW()
                """)
                .setParameter(1, key)
                .setParameter(2, value)
                .setParameter(3, valueType)
                .executeUpdate();
    }
}
