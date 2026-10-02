package org.github.tess1o.geopulse.mapmatching.repository;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingStatus;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingTargetReset;
import org.github.tess1o.geopulse.mapmatching.model.TimelineTripPathMatchEntity;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.TimelineStatus;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for issue #608: a historical trip that once failed kept its terminal
 * map-matching verdict forever, because nothing ever moved it back to PENDING. "Rebuild Historical
 * Queue" only rewound the reconciliation cursor, so it re-walked the history and re-attached the
 * same FAILED/SKIPPED rows without ever calling Valhalla again.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineTripPathMatchRepositoryResetIntegrationTest {

    private static final String HTTP_400_ERROR =
            "Valhalla trace_route failed with HTTP 400: {\"error_code\":443,\"error\":\"Exact route match "
                    + "algorithm failed to find path\"}";

    @Inject
    TimelineTripPathMatchRepository matchRepository;

    @Inject
    EntityManager entityManager;

    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("DELETE FROM timeline_trip_path_matches").executeUpdate();
            userId = persistUser("reset-owner");
            otherUserId = persistUser("reset-other-user");
        });
    }

    @Test
    void resetTerminalTargetsRequeuesUnsuccessfulTargetsAndPurgesDetachedOnes() {
        Instant oneHourFromNow = Instant.now().plus(1, ChronoUnit.HOURS);

        Seed seed = QuarkusTransaction.requiringNew().call(() -> {
            UserEntity owner = entityManager.find(UserEntity.class, userId);
            UserEntity other = entityManager.find(UserEntity.class, otherUserId);

            TimelineTripEntity failedTrip = persistTrip(owner, Instant.parse("2026-09-16T04:45:00Z"));
            TimelineTripEntity skippedTrip = persistTrip(owner, Instant.parse("2026-09-16T17:10:00Z"));
            TimelineTripEntity matchedTrip = persistTrip(owner, Instant.parse("2026-09-15T06:00:00Z"));
            TimelineTripEntity pendingTrip = persistTrip(owner, Instant.parse("2026-09-14T06:00:00Z"));
            TimelineTripEntity otherUserTrip = persistTrip(other, Instant.parse("2026-09-13T06:00:00Z"));

            return new Seed(
                    insertMatch(owner, failedTrip.getId(), MapMatchingStatus.FAILED, 3, "hash-failed",
                            Instant.now(), HTTP_400_ERROR, null),
                    insertMatch(owner, skippedTrip.getId(), MapMatchingStatus.SKIPPED, 1, "hash-skipped",
                            Instant.now(), "Trip has fewer than two eligible GPS points", null),
                    insertMatch(owner, matchedTrip.getId(), MapMatchingStatus.MATCHED, 1, "hash-matched",
                            Instant.now(), null, "[[{\"latitude\":50.45,\"longitude\":30.52}]]"),
                    insertMatch(owner, pendingTrip.getId(), MapMatchingStatus.PENDING, 2, "hash-pending",
                            oneHourFromNow, "Retrying after transient Valhalla failure", null),
                    insertMatch(owner, null, MapMatchingStatus.FAILED, 4, "hash-detached",
                            Instant.now(), HTTP_400_ERROR, null),
                    insertMatch(other, otherUserTrip.getId(), MapMatchingStatus.FAILED, 1, "hash-other",
                            Instant.now(), HTTP_400_ERROR, null));
        });

        MapMatchingTargetReset reset = QuarkusTransaction.requiringNew()
                .call(() -> matchRepository.resetTerminalTargets());

        assertThat(reset.requeuedTargets()).isEqualTo(3L);
        assertThat(reset.purgedDetachedTargets()).isEqualTo(1L);

        TimelineTripPathMatchEntity failed = matchRow(seed.failedMatchId());
        assertThat(failed.getStatus()).isEqualTo(MapMatchingStatus.PENDING);
        assertThat(failed.getAttempts()).isZero();
        assertThat(failed.getCompletedAt()).isNull();
        assertThat(failed.getLastError()).isNull();
        assertThat(failed.getMatchedSegmentsJson()).isNull();
        assertThat(failed.getNextAttemptAt()).isBeforeOrEqualTo(Instant.now());

        TimelineTripPathMatchEntity skipped = matchRow(seed.skippedMatchId());
        assertThat(skipped.getStatus()).isEqualTo(MapMatchingStatus.PENDING);
        assertThat(skipped.getAttempts()).isZero();
        assertThat(skipped.getCompletedAt()).isNull();
        assertThat(skipped.getLastError()).isNull();

        // A successful match is left untouched - re-matching it would only spend Valhalla calls.
        TimelineTripPathMatchEntity matched = matchRow(seed.matchedMatchId());
        assertThat(matched.getStatus()).isEqualTo(MapMatchingStatus.MATCHED);
        assertThat(matched.getAttempts()).isEqualTo(1);
        assertThat(matched.getCompletedAt()).isNotNull();
        assertThat(matched.getMatchedSegmentsJson()).isEqualTo("[[{\"latitude\":50.45,\"longitude\":30.52}]]");

        // A pending target may legitimately be in retry backoff; its schedule must not be pulled forward.
        TimelineTripPathMatchEntity pending = matchRow(seed.pendingMatchId());
        assertThat(pending.getStatus()).isEqualTo(MapMatchingStatus.PENDING);
        assertThat(pending.getAttempts()).isEqualTo(2);
        assertThat(pending.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(pending.getLastError()).isEqualTo("Retrying after transient Valhalla failure");

        assertThat(countDetachedRows()).isZero();

        // The reset is global, matching restartAllTripOwners.
        assertThat(matchRow(seed.otherUserMatchId()).getStatus()).isEqualTo(MapMatchingStatus.PENDING);
    }

    @Test
    void resetTerminalTargetsMakesPreviouslyStuckTargetsClaimableAgain() {
        long stuckTripId = QuarkusTransaction.requiringNew().call(() -> {
            UserEntity owner = entityManager.find(UserEntity.class, userId);
            TimelineTripEntity trip = persistTrip(owner, Instant.parse("2026-09-16T04:45:00Z"));
            insertMatch(owner, trip.getId(), MapMatchingStatus.FAILED, 3, "hash-stuck",
                    Instant.now(), HTTP_400_ERROR, null);
            return trip.getId();
        });

        assertThat(QuarkusTransaction.requiringNew().call(() -> matchRepository.claimPending(10, true, true)))
                .as("a terminally FAILED target is invisible to the worker")
                .isEmpty();

        QuarkusTransaction.requiringNew().run(() -> matchRepository.resetTerminalTargets());

        List<TimelineTripPathMatchEntity> claimed = QuarkusTransaction.requiringNew()
                .call(() -> matchRepository.claimPending(10, true, true));

        assertThat(claimed).hasSize(1);
        assertThat(claimed.getFirst().getTrip().getId()).isEqualTo(stuckTripId);
        assertThat(claimed.getFirst().getStatus()).isEqualTo(MapMatchingStatus.PROCESSING);
    }

    @Test
    void deleteAllTargetsRemovesSuccessfulMatchesToo() {
        QuarkusTransaction.requiringNew().run(() -> {
            UserEntity owner = entityManager.find(UserEntity.class, userId);
            TimelineTripEntity trip = persistTrip(owner, Instant.parse("2026-09-16T04:45:00Z"));
            insertMatch(owner, trip.getId(), MapMatchingStatus.MATCHED, 1, "hash-keep",
                    Instant.now(), null, "[[{\"latitude\":50.45,\"longitude\":30.52}]]");
        });

        long deleted = QuarkusTransaction.requiringNew().call(() -> matchRepository.deleteAllTargets());

        assertThat(deleted).isEqualTo(1L);
        assertThat(countAllRows()).isZero();
    }

    private TimelineTripPathMatchEntity matchRow(long id) {
        return QuarkusTransaction.requiringNew()
                .call(() -> entityManager.find(TimelineTripPathMatchEntity.class, id));
    }

    private long countAllRows() {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager.createNativeQuery(
                "SELECT count(*) FROM timeline_trip_path_matches")
                .getSingleResult()).longValue());
    }

    private long countDetachedRows() {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager.createNativeQuery(
                "SELECT count(*) FROM timeline_trip_path_matches WHERE trip_id IS NULL")
                .getSingleResult()).longValue());
    }

    private UUID persistUser(String prefix) {
        UserEntity user = UserEntity.builder()
                .email(prefix + "-" + UUID.randomUUID() + "@example.com")
                .passwordHash("test")
                .fullName("Map Matching Reset Test")
                .createdAt(Instant.now())
                .isActive(true)
                .role(Role.USER)
                .timezone("UTC")
                .timelineStatus(TimelineStatus.IDLE)
                .build();
        entityManager.persist(user);
        entityManager.flush();
        return user.getId();
    }

    private TimelineTripEntity persistTrip(UserEntity owner, Instant timestamp) {
        TimelineTripEntity trip = TimelineTripEntity.builder()
                .user(owner)
                .timestamp(timestamp)
                .tripDuration(300)
                .distanceMeters(1_000)
                .startPoint(GeoUtils.createPoint(30.5234, 50.4501))
                .endPoint(GeoUtils.createPoint(30.5334, 50.4601))
                .movementType("CAR")
                .build();
        entityManager.persist(trip);
        entityManager.flush();
        return trip;
    }

    private long insertMatch(UserEntity owner, Long tripId, MapMatchingStatus status, int attempts,
                             String inputHash, Instant nextAttemptAt, String lastError, String matchedSegmentsJson) {
        Instant completedAt = switch (status) {
            case MATCHED, FAILED, SKIPPED -> Instant.now();
            default -> null;
        };
        return ((Number) entityManager.createNativeQuery("""
                INSERT INTO timeline_trip_path_matches (
                    trip_id, user_id, provider, profile, config_hash, input_hash, status, attempts,
                    next_attempt_at, completed_at, last_error, matched_segments_json,
                    source, priority, created_at, updated_at
                )
                VALUES (?1, ?2, 'valhalla', 'auto', 'algorithm=v4|test', ?3, ?4, ?5, ?6, ?7, ?8, ?9,
                        'HISTORICAL', 10, NOW(), NOW())
                RETURNING id
                """)
                .setParameter(1, tripId)
                .setParameter(2, owner.getId())
                .setParameter(3, inputHash)
                .setParameter(4, status.name())
                .setParameter(5, attempts)
                .setParameter(6, nextAttemptAt)
                .setParameter(7, completedAt)
                .setParameter(8, lastError)
                .setParameter(9, matchedSegmentsJson)
                .getSingleResult()).longValue();
    }

    private record Seed(long failedMatchId, long skippedMatchId, long matchedMatchId,
                        long pendingMatchId, long detachedMatchId, long otherUserMatchId) {
    }
}
