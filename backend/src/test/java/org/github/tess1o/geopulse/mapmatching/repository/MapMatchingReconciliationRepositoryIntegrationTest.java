package org.github.tess1o.geopulse.mapmatching.repository;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingReconciliation;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingSource;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
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
class MapMatchingReconciliationRepositoryIntegrationTest {

    @Inject
    MapMatchingReconciliationRepository reconciliationRepository;

    @Inject
    EntityManager entityManager;

    private UUID userId;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("DELETE FROM map_matching_reconciliations").executeUpdate();

            UserEntity user = UserEntity.builder()
                    .email("map-matching-reconciliation-" + UUID.randomUUID() + "@example.com")
                    .passwordHash("test")
                    .fullName("Map Matching Reconciliation Test")
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
    void completedReconciliationAdvancesScannedTripsToTotalTrips() {
        Instant rangeStart = Instant.parse("2026-04-01T00:00:00Z");
        Instant rangeEnd = Instant.parse("2026-05-01T00:00:00Z");
        long reconciliationId = QuarkusTransaction.requiringNew().call(() -> insertReconciliation(
                rangeStart, rangeEnd, 1_616, 1_565));

        MapMatchingReconciliation reconciliation = new MapMatchingReconciliation(
                reconciliationId,
                userId,
                MapMatchingSource.HISTORICAL,
                rangeStart,
                rangeEnd,
                rangeStart,
                0,
                rangeStart,
                1_616,
                1_565,
                null
        );

        QuarkusTransaction.requiringNew().run(() ->
                reconciliationRepository.advance(reconciliation, rangeEnd, Long.MAX_VALUE, 0, true));

        long scannedTrips = QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager.createNativeQuery("""
                SELECT scanned_trips
                FROM map_matching_reconciliations
                WHERE id = ?1
                """)
                .setParameter(1, reconciliationId)
                .getSingleResult()).longValue());

        assertThat(scannedTrips).isEqualTo(1_616);
    }

    @Test
    void enqueueCreatesReconciliationEvenWhenRangeHasNoTripsYet() {
        Instant rangeStart = Instant.parse("2026-08-25T12:00:00Z");
        Instant rangeEnd = Instant.parse("2026-08-25T13:00:00Z");

        QuarkusTransaction.requiringNew().run(() ->
                reconciliationRepository.enqueue(userId, rangeStart, rangeEnd, MapMatchingSource.AUTOMATIC, rangeStart));

        long pendingRows = countReconciliations();

        assertThat(pendingRows).isOne();
    }

    @Test
    void enqueueCreatesReconciliationWhenRangeHasTrips() {
        Instant tripStart = Instant.parse("2026-08-25T12:30:00Z");
        Instant rangeStart = Instant.parse("2026-08-25T12:00:00Z");
        Instant rangeEnd = Instant.parse("2026-08-25T13:00:00Z");

        QuarkusTransaction.requiringNew().run(() -> {
            UserEntity user = entityManager.find(UserEntity.class, userId);
            entityManager.persist(TimelineTripEntity.builder()
                    .user(user)
                    .timestamp(tripStart)
                    .tripDuration(300)
                    .distanceMeters(1_000)
                    .startPoint(GeoUtils.createPoint(30.5234, 50.4501))
                    .endPoint(GeoUtils.createPoint(30.5334, 50.4601))
                    .movementType("WALK")
                    .build());
            reconciliationRepository.enqueue(userId, rangeStart, rangeEnd, MapMatchingSource.AUTOMATIC, rangeStart);
        });

        Object[] row = QuarkusTransaction.requiringNew().call(() -> (Object[]) entityManager.createNativeQuery("""
                SELECT source, total_trips, scanned_trips, completed_at
                FROM map_matching_reconciliations
                WHERE user_id = ?1
                """)
                .setParameter(1, userId)
                .getSingleResult());

        assertThat(row[0]).isEqualTo(MapMatchingSource.AUTOMATIC.name());
        assertThat(((Number) row[1]).longValue()).isEqualTo(1L);
        assertThat(((Number) row[2]).longValue()).isZero();
        assertThat(row[3]).isNull();
    }

    @Test
    void refreshEligiblePendingTripTotalsCompletesOnlyRangesWithoutTrips() {
        Instant emptyStart = Instant.parse("2026-08-25T12:00:00Z");
        Instant emptyEnd = Instant.parse("2026-08-25T13:00:00Z");
        Instant nonEmptyStart = Instant.parse("2026-08-25T14:00:00Z");
        Instant nonEmptyEnd = Instant.parse("2026-08-25T15:00:00Z");
        long emptyId = QuarkusTransaction.requiringNew().call(() ->
                insertReconciliation(emptyStart, emptyEnd, 0, 0));
        long nonEmptyId = QuarkusTransaction.requiringNew().call(() -> {
            UserEntity user = entityManager.find(UserEntity.class, userId);
            entityManager.persist(TimelineTripEntity.builder()
                    .user(user)
                    .timestamp(Instant.parse("2026-08-25T14:30:00Z"))
                    .tripDuration(300)
                    .distanceMeters(1_000)
                    .startPoint(GeoUtils.createPoint(30.5234, 50.4501))
                    .endPoint(GeoUtils.createPoint(30.5334, 50.4601))
                    .movementType("WALK")
                    .build());
            return insertReconciliation(nonEmptyStart, nonEmptyEnd, 0, 0, MapMatchingSource.AUTOMATIC);
        });

        long refreshed = QuarkusTransaction.requiringNew().call(() -> reconciliationRepository.refreshEligiblePendingTripTotals());

        assertThat(refreshed).isEqualTo(2L);
        assertThat(completedAt(emptyId)).isNotNull();
        assertThat(completedAt(nonEmptyId)).isNull();
    }

    @Test
    void refreshEligiblePendingTripTotalsKeepsInitiallyEmptyRangeWhenTripsAppearLater() {
        Instant rangeStart = Instant.parse("2026-08-25T12:00:00Z");
        Instant rangeEnd = Instant.parse("2026-08-25T13:00:00Z");
        long reconciliationId = QuarkusTransaction.requiringNew().call(() ->
                insertReconciliation(rangeStart, rangeEnd, 0, 0));

        QuarkusTransaction.requiringNew().run(() -> {
            UserEntity user = entityManager.find(UserEntity.class, userId);
            entityManager.persist(TimelineTripEntity.builder()
                    .user(user)
                    .timestamp(Instant.parse("2026-08-25T12:30:00Z"))
                    .tripDuration(300)
                    .distanceMeters(1_000)
                    .startPoint(GeoUtils.createPoint(30.5234, 50.4501))
                    .endPoint(GeoUtils.createPoint(30.5334, 50.4601))
                    .movementType("WALK")
                    .build());
        });

        long refreshed = QuarkusTransaction.requiringNew().call(() -> reconciliationRepository.refreshEligiblePendingTripTotals());

        Object[] row = QuarkusTransaction.requiringNew().call(() -> (Object[]) entityManager.createNativeQuery("""
                SELECT total_trips, scanned_trips, completed_at
                FROM map_matching_reconciliations
                WHERE id = ?1
                """)
                .setParameter(1, reconciliationId)
                .getSingleResult());

        assertThat(refreshed).isEqualTo(1L);
        assertThat(((Number) row[0]).longValue()).isEqualTo(1L);
        assertThat(((Number) row[1]).longValue()).isZero();
        assertThat(row[2]).isNull();
    }

    private long countReconciliations() {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager.createNativeQuery("""
                SELECT count(*)
                FROM map_matching_reconciliations
                WHERE user_id = ?1
                """)
                .setParameter(1, userId)
                .getSingleResult()).longValue());
    }

    private Object completedAt(long reconciliationId) {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.createNativeQuery("""
                SELECT completed_at
                FROM map_matching_reconciliations
                WHERE id = ?1
                """)
                .setParameter(1, reconciliationId)
                .getSingleResult());
    }

    private long insertReconciliation(Instant rangeStart, Instant rangeEnd, long totalTrips, long scannedTrips) {
        return insertReconciliation(rangeStart, rangeEnd, totalTrips, scannedTrips, MapMatchingSource.HISTORICAL);
    }

    private long insertReconciliation(Instant rangeStart, Instant rangeEnd, long totalTrips, long scannedTrips,
                                      MapMatchingSource source) {
        return ((Number) entityManager.createNativeQuery("""
                INSERT INTO map_matching_reconciliations (
                    user_id, source, range_start, range_end, cursor_at, cursor_trip_id,
                    eligible_at, total_trips, scanned_trips, completed_at, created_at, updated_at
                )
                VALUES (?1, ?2, ?3, ?4, ?3, 0, NOW(), ?5, ?6, NULL, NOW(), NOW())
                RETURNING id
                """)
                .setParameter(1, userId)
                .setParameter(2, source.name())
                .setParameter(3, rangeStart)
                .setParameter(4, rangeEnd)
                .setParameter(5, totalTrips)
                .setParameter(6, scannedTrips)
                .getSingleResult()).longValue();
    }
}
