package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.TimelineUserLock;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.CorrectionTestPayloads.set;

/**
 * A correction rebuild that has to wait for another generation of the same user must plan its window from what that
 * generation left, not from what it read before waiting.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class CorrectionWindowLockIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    TimelineEditService edits;

    @Inject
    StreamingTimelineGenerationService generation;

    @Inject
    TimelineUserLock userLock;

    @Inject
    TimelineStayRepository stays;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void correctionWindowWaitingForAnotherGenerationPlansFromItsResult() throws Exception {
        UUID userId = harness.createUser("window-lock");
        Track track = new Track();
        double[] home = track.place(49.8420, 24.0310);
        double[] office = track.place(49.8350, 24.0750);
        double[] cafe = track.place(49.8390, 24.0500);
        double[] gym = track.place(49.8452, 24.0602);
        List<OwnTracksLocationMessage> day = track
                .stay(home, at("06:00"), at("08:00"), 5)
                .drive(home, office, at("08:01"), at("08:20"))
                .stay(office, at("08:21"), at("12:00"), 10)
                .drive(office, cafe, at("12:01"), at("12:20"))
                .stay(cafe, at("12:21"), at("13:30"), 5)
                .drive(cafe, gym, at("13:31"), at("13:59"))
                .stay(gym, at("14:00"), at("16:00"), 10)
                .drive(gym, home, at("16:01"), at("16:29"))
                .stay(home, at("16:30"), at("20:00"), 10)
                .messages();
        // Uploaded late: the morning at home began an hour earlier, which moves the stay the window restarts at.
        List<OwnTracksLocationMessage> late = track.stay(home, at("05:00"), at("05:55"), 5).messages()
                .subList(day.size(), day.size() + 12);
        harness.ingest(userId, day);
        harness.process(userId);
        edits.saveGrouped(userId, label(stayAt(userId, at("12:21")), "Lunch"));

        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            // The real-time job holds the user's timeline, as RealtimeTimelineProcessor does, while the
            // correction rebuild starts.
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch go = new CountDownLatch(1);
            CompletableFuture<Void> realtime = CompletableFuture.runAsync(() -> QuarkusTransaction.requiringNew()
                    .run(() -> {
                        assertThat(userLock.tryLock(userId)).isTrue();
                        locked.countDown();
                        await(go);
                        generation.regeneratePending(userId, null, "realtime");
                    }), threads);
            assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();

            CompletableFuture<Integer> correctionBackend = new CompletableFuture<>();
            CompletableFuture<Void> correction = CompletableFuture.runAsync(() -> QuarkusTransaction.requiringNew()
                    .run(() -> {
                        correctionBackend.complete(((Number) entityManager
                                .createNativeQuery("SELECT pg_backend_pid()").getSingleResult()).intValue());
                        generation.regeneratePending(userId, null, "correction");
                    }), threads);
            awaitWaitingForUserLock(correctionBackend.get(30, TimeUnit.SECONDS));

            harness.ingest(userId, late);
            go.countDown();
            realtime.get(60, TimeUnit.SECONDS);
            correction.get(60, TimeUnit.SECONDS);
        } finally {
            threads.shutdownNow();
        }

        if (harness.pendingRebuild(userId).isPresent()) {
            generation.regeneratePending(userId, null, "test");
        }
        assertThat(harness.snapshot(userId).stays())
                .anySatisfy(stay -> assertThat(stay).startsWith("STAY 2026-09-08T05:00:00Z"))
                .noneSatisfy(stay -> assertThat(stay).startsWith("STAY 2026-09-08T06:00:00Z"));
        harness.assertMatchesFullRebuild(userId, "a correction rebuild that waited for the real-time job");
    }

    /** Waits until the correction rebuild's connection waits for the user's row, which the real-time job holds. */
    private void awaitWaitingForUserLock(int backend) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            boolean waiting = QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager.createNativeQuery("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE pid = ?1 AND wait_event_type = 'Lock' AND query ILIKE '%update users%'
                    """).setParameter(1, backend).getSingleResult()).longValue() > 0);
            if (waiting) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("the correction rebuild never waited for the user's timeline");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private TimelineStayEntity stayAt(UUID userId, Instant start) {
        return QuarkusTransaction.requiringNew().call(() ->
                stays.find("user.id = ?1 and timestamp = ?2", userId, start).firstResult());
    }

    private static TimelineEditRequest label(TimelineStayEntity stay, String name) {
        var request = new TimelineEditRequest();
        request.setKind(TimelineEditKind.SET_VISIT_NAME);
        request.setAnchorStart(stay.getTimestamp());
        request.setAnchorEnd(stay.getTimestamp().plusSeconds(stay.getStayDuration()));
        set(request, new LinkedHashMap<>(Map.of("visitName", name)));
        return request;
    }

    private static Instant at(String time) {
        return Instant.parse("2026-09-08T" + time + ":00Z");
    }
}
