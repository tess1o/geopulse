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

    private long insertReconciliation(Instant rangeStart, Instant rangeEnd, long totalTrips, long scannedTrips) {
        return ((Number) entityManager.createNativeQuery("""
                INSERT INTO map_matching_reconciliations (
                    user_id, source, range_start, range_end, cursor_at, cursor_trip_id,
                    eligible_at, total_trips, scanned_trips, completed_at, created_at, updated_at
                )
                VALUES (?1, 'HISTORICAL', ?2, ?3, ?2, 0, NOW(), ?4, ?5, NULL, NOW(), NOW())
                RETURNING id
                """)
                .setParameter(1, userId)
                .setParameter(2, rangeStart)
                .setParameter(3, rangeEnd)
                .setParameter(4, totalTrips)
                .setParameter(5, scannedTrips)
                .getSingleResult()).longValue();
    }
}
