package org.github.tess1o.geopulse.weather.repository;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.TimelineStatus;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.weather.model.WeatherTargetSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class WeatherBackfillRepositoryIntegrationTest {

    @Inject
    WeatherBackfillReconciliationRepository reconciliationRepository;

    @Inject
    WeatherSampleTargetRepository targetRepository;

    @Inject
    EntityManager entityManager;

    private UUID userId;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("DELETE FROM weather_backfill_reconciliations").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM weather_sample_targets").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM weather_samples").executeUpdate();

            UserEntity user = UserEntity.builder()
                    .email("weather-backfill-" + UUID.randomUUID() + "@example.com")
                    .passwordHash("test")
                    .fullName("Weather Backfill Test")
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

    @Test
    void reconciliationRangesCoalesceAndAdvanceInBoundedChunks() {
        Instant originalStart = Instant.parse("2025-01-01T00:00:00Z");
        Instant originalEnd = Instant.parse("2025-12-31T00:00:00Z");
        Instant earlierStart = Instant.parse("2024-06-01T00:00:00Z");

        QuarkusTransaction.requiringNew().run(() -> {
            reconciliationRepository.enqueue(userId, originalStart, originalEnd);
            reconciliationRepository.enqueue(userId, earlierStart, originalStart);
        });

        WeatherBackfillReconciliation first = QuarkusTransaction.requiringNew().call(() -> {
            WeatherBackfillReconciliation claimed = reconciliationRepository.claimNext(
                    originalEnd.plusSeconds(1), Duration.ofDays(90));
            reconciliationRepository.completeChunk(claimed);
            return claimed;
        });

        assertThat(first.chunkStart()).isEqualTo(earlierStart);
        assertThat(first.chunkEnd()).isEqualTo(earlierStart.plus(Duration.ofDays(90)));

        WeatherBackfillReconciliation second = QuarkusTransaction.requiringNew().call(() ->
                reconciliationRepository.claimNext(originalEnd.plusSeconds(1), Duration.ofDays(90)));
        assertThat(second.chunkStart()).isEqualTo(first.chunkEnd());
    }

    @Test
    void batchInsertSkipsExistingTargetAndExistingSample() {
        Instant targetAt = Instant.parse("2025-06-01T10:00:00Z");
        WeatherTargetBatchRow target = new WeatherTargetBatchRow(50.45, 30.52, 50.45, 30.52, targetAt);

        int firstInsert = QuarkusTransaction.requiringNew().call(() -> targetRepository.enqueueMissingBatch(
                userId, "OPEN_METEO", List.of(target), WeatherTargetSource.HISTORICAL_BACKFILL, 70));
        int secondInsert = QuarkusTransaction.requiringNew().call(() -> targetRepository.enqueueMissingBatch(
                userId, "OPEN_METEO", List.of(target), WeatherTargetSource.HISTORICAL_BACKFILL, 70));

        assertThat(firstInsert).isOne();
        assertThat(secondInsert).isZero();

        Instant sampledAt = targetAt.plusSeconds(3600);
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
                INSERT INTO weather_samples (
                    user_id, provider, source, requested_latitude, requested_longitude,
                    latitude_bucket, longitude_bucket, observed_at, fetched_at, created_at, updated_at
                )
                VALUES (?1, 'OPEN_METEO', 'HISTORICAL_BACKFILL', 50.45, 30.52,
                        50.45, 30.52, ?2, NOW(), NOW(), NOW())
                """)
                .setParameter(1, userId)
                .setParameter(2, sampledAt)
                .executeUpdate());

        int sampledInsert = QuarkusTransaction.requiringNew().call(() -> targetRepository.enqueueMissingBatch(
                userId,
                "OPEN_METEO",
                List.of(new WeatherTargetBatchRow(50.45, 30.52, 50.45, 30.52, sampledAt)),
                WeatherTargetSource.HISTORICAL_BACKFILL,
                70
        ));
        assertThat(sampledInsert).isZero();
    }

    @Test
    void nearestTripCoordinateUsesPointsImmediatelyAroundTargetTime() {
        Instant tripStart = Instant.parse("2025-06-01T10:00:00Z");
        QuarkusTransaction.requiringNew().run(() -> {
            insertGpsPoint(tripStart.plusSeconds(60 * 60), 50.41, 30.51);
            insertGpsPoint(tripStart.plusSeconds(3 * 60 * 60), 50.43, 30.53);
            insertGpsPoint(tripStart.plusSeconds(7 * 60 * 60), 50.47, 30.57);
        });

        double[] coordinate = QuarkusTransaction.requiringNew().call(() ->
                reconciliationRepository.findNearestTripCoordinate(
                        userId,
                        tripStart,
                        8 * 60 * 60,
                        tripStart.plusSeconds(2 * 60 * 60 + 40 * 60)
                ).orElseThrow());

        assertThat(coordinate).containsExactly(50.43, 30.53);
    }

    private void insertGpsPoint(Instant timestamp, double latitude, double longitude) {
        entityManager.createNativeQuery("""
                INSERT INTO gps_points (
                    user_id, coordinates, timestamp, source_type, created_at
                )
                VALUES (?1, ST_SetSRID(ST_MakePoint(?2, ?3), 4326), ?4, 'TEST', NOW())
                """)
                .setParameter(1, userId)
                .setParameter(2, longitude)
                .setParameter(3, latitude)
                .setParameter(4, timestamp)
                .executeUpdate();
    }
}
