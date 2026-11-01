package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditStatus;
import org.github.tess1o.geopulse.streaming.jobs.TimelineSafetyNetJob;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineEditEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;
import org.github.tess1o.geopulse.streaming.service.TimelineSafetyNetService;
import org.github.tess1o.geopulse.streaming.service.TimelineSafetyNetService.Outcome;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.uploadBatches;

/**
 * The nightly safety net: rebuilds the recent part of active timelines, leaves correct ones alone in content, fixes
 * and reports wrong ones, and never queues behind a busy user.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineSafetyNetIntegrationTest {

    private static final Duration WINDOW = Duration.ofHours(48);

    @Inject
    TimelineReplayHarness harness;

    @Inject org.github.tess1o.geopulse.streaming.edits.TimelineEditService edits;

    @Inject
    TimelineSafetyNetService safetyNetService;

    @Inject
    TimelineSafetyNetJob safetyNetJob;

    @Inject
    TimelineJobProgressService jobProgressService;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void timelineBuiltByTheRealtimeJobNeedsNoCorrection() {
        UUID userId = userWithRecentDays("safety-unchanged", 51);
        Instant now = Instant.now();

        assertThat(safetyNetService.verifyUser(userId, now.minus(WINDOW))).isEqualTo(Outcome.UNCHANGED);

        harness.assertMatchesFullRebuild(userId, "after the safety net");
    }

    @Test
    void editedTimelineNeedsNoSafetyNetCorrection() {
        UUID userId = userWithRecentDays("safety-edited", 57);
        TimelineStayEntity stay = QuarkusTransaction.requiringNew().call(() -> entityManager.createQuery(
                        "select s from TimelineStayEntity s where s.user.id = :userId order by s.timestamp",
                        TimelineStayEntity.class)
                .setParameter("userId", userId).setMaxResults(1).getSingleResult());
        QuarkusTransaction.requiringNew().run(() -> entityManager.persist(TimelineEditEntity.builder()
                .user(entityManager.getReference(UserEntity.class, userId)).seq(1)
                .kind(TimelineEditKind.SET_VISIT_NAME)
                .anchorStart(stay.getTimestamp()).anchorEnd(stay.getTimestamp().plusSeconds(stay.getStayDuration()))
                .affectedFrom(stay.getTimestamp()).affectedTo(stay.getTimestamp().plusSeconds(stay.getStayDuration()))
                .payload(new LinkedHashMap<>(Map.of("visitName", "Safety checked")))
                .status(TimelineEditStatus.ACTIVE).build()));
        harness.fullRebuild(userId);

        assertThat(safetyNetService.verifyUser(userId, Instant.now().minus(WINDOW))).isEqualTo(Outcome.UNCHANGED);
        assertThat(QuarkusTransaction.requiringNew().call(() -> entityManager.createQuery(
                        "select count(s) from TimelineStayEntity s where s.user.id = :userId and s.visitName = :name",
                        Long.class)
                .setParameter("userId", userId).setParameter("name", "Safety checked").getSingleResult()))
                .isEqualTo(1);
    }

    @Test
    void safetyNetRepairsMissingStructuralOutputAndRetainsItsApplication() {
        UUID user = userWithRecentDays("safety-structural", 58);
        var stay = QuarkusTransaction.requiringNew().call(() -> entityManager.createQuery(
                "select s from TimelineStayEntity s where s.user.id=:user and s.stayDuration>7200 order by s.timestamp desc", TimelineStayEntity.class)
                .setParameter("user", user).setMaxResults(1).getSingleResult());
        var request = new org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest();
        request.setKind(TimelineEditKind.SPLIT_STAY); request.setAnchorStart(stay.getTimestamp());
        request.setAnchorEnd(stay.getTimestamp().plusSeconds(stay.getStayDuration()));
        request.setPayload(new LinkedHashMap<>(Map.of("splitAt", stay.getTimestamp().plusSeconds(3600).toString(),
                "rightLocationName", "Safety stop", "rightLatitude", 50.0, "rightLongitude", 30.0)));
        var edit = edits.create(user, request); harness.fullRebuild(user);
        assertThat(harness.pendingRebuild(user)).isEmpty();
        var expected = harness.snapshot(user).content();
        QuarkusTransaction.requiringNew().run(() -> entityManager.createQuery(
                "delete from TimelineStayEntity s where s.user.id=:user and s.locationName='Safety stop'")
                .setParameter("user", user).executeUpdate());
        assertThat(harness.snapshot(user).content()).isNotEqualTo(expected);
        assertThat(safetyNetService.verifyUser(user, stay.getTimestamp().minusSeconds(60))).isEqualTo(Outcome.CORRECTED);
        assertThat(harness.snapshot(user).content()).isEqualTo(expected);
        assertThat(edits.list(user)).filteredOn(row -> row.id().equals(edit.id())).singleElement().satisfies(row -> {
            assertThat(row.applied()).isTrue(); assertThat(row.target()).isNotNull();
        });
    }

    @Test
    void wrongTimelineIsCorrected() {
        UUID userId = userWithRecentDays("safety-corrected", 52);
        List<String> correct = harness.snapshot(userId).content();
        deleteLatestStay(userId);
        assertThat(harness.snapshot(userId).content()).isNotEqualTo(correct);

        assertThat(safetyNetService.verifyUser(userId, Instant.now().minus(WINDOW))).isEqualTo(Outcome.CORRECTED);

        assertThat(harness.snapshot(userId).content()).isEqualTo(correct);
    }

    @Test
    void userWithAPendingRebuildIsLeftToTheRealtimeJob() {
        UUID userId = userWithRecentDays("safety-pending", 53);
        Instant from = Instant.now().minus(WINDOW);
        harness.ingest(userId, List.of(Track.message(49.8420, 24.0310, Instant.now().minusSeconds(60), 10.0, 0.0)));

        assertThat(safetyNetService.verifyUser(userId, from)).isEqualTo(Outcome.DEFERRED);

        // The pending request now also covers the safety net's window.
        assertThat(harness.pendingRebuild(userId).orElseThrow().rebuildFrom()).isBeforeOrEqualTo(from);
    }

    @Test
    void userWithARunningJobIsDeferred() {
        UUID userId = userWithRecentDays("safety-busy", 54);
        Instant from = Instant.now().minus(WINDOW).truncatedTo(ChronoUnit.MICROS); // Postgres keeps microseconds
        UUID jobId = jobProgressService.createJob(userId);
        try {
            assertThat(safetyNetService.verifyUser(userId, from)).isEqualTo(Outcome.DEFERRED);
            assertThat(harness.pendingRebuild(userId).orElseThrow().rebuildFrom()).isEqualTo(from);
        } finally {
            jobProgressService.completeJob(jobId);
        }
    }

    @Test
    void onlyRecentlyActiveUsersAreChecked() {
        UUID recent = userWithRecentDays("safety-recent", 55);
        UUID inactive = harness.createUser("safety-inactive");
        harness.ingest(inactive, SyntheticDays.generate(56, LocalDate.of(2026, 9, 1), 1));
        harness.process(inactive);

        Instant now = Instant.now();
        List<UUID> checked = safetyNetService.usersWithPointsSince(now.minus(WINDOW));
        assertThat(checked).contains(recent).doesNotContain(inactive);

        assertThat(safetyNetJob.run(now)).containsKey(Outcome.UNCHANGED);
        harness.assertMatchesFullRebuild(recent, "after a safety net run");
    }

    /** Two full days ending last midnight, delivered as live uploads every 30 minutes. */
    private UUID userWithRecentDays(String prefix, long seed) {
        UUID userId = harness.createUserWithFavoriteAreas(prefix, SyntheticDays.NAMED_PLACES);
        LocalDate firstDay = LocalDate.now(SyntheticDays.ZONE).minusDays(2);
        List<OwnTracksLocationMessage> days = SyntheticDays.generate(seed, firstDay, 2);
        harness.replayLive(userId, uploadBatches(days, 30));
        assertThat(harness.pendingRebuild(userId)).isEmpty();
        return userId;
    }

    private void deleteLatestStay(UUID userId) {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
                        DELETE FROM timeline_stays WHERE id =
                            (SELECT id FROM timeline_stays WHERE user_id = ?1 ORDER BY timestamp DESC LIMIT 1)
                        """)
                .setParameter(1, userId)
                .executeUpdate());
    }
}
