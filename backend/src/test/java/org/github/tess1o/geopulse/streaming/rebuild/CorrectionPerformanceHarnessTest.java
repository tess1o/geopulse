package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.CorrectionTestPayloads.set;

/**
 * Measures timeline corrections on a large account. Off by default; run it with
 * {@code GEOPULSE_PERFORMANCE=true GEOPULSE_PERFORMANCE_POINTS=5000000 mvn verify -Dtest=none
 * -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=CorrectionPerformanceHarnessTest
 * -Dfailsafe.failIfNoSpecifiedTests=false}. The recording is a commute every day, one point every 30 seconds: one
 * million points are about eleven months, five million about four and three quarter years.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
@EnabledIfEnvironmentVariable(named = "GEOPULSE_PERFORMANCE", matches = "true")
class CorrectionPerformanceHarnessTest {

    private static final Instant START = Instant.parse("2021-01-04T00:00:00Z");

    @Inject TimelineReplayHarness harness;
    @Inject TimelineEditService edits;
    @Inject StreamingTimelineGenerationService generation;
    @Inject TimelineStayRepository stays;
    @Inject EntityManager em;

    @Test
    void measureCorrectionsOnALargeAccount() {
        GeocodingTestMocks.install();
        long points = Long.parseLong(System.getenv().getOrDefault("GEOPULSE_PERFORMANCE_POINTS", "1000000"));
        int corrections = Integer.parseInt(System.getenv().getOrDefault("GEOPULSE_PERFORMANCE_CORRECTIONS", "500"));
        List<String> report = new ArrayList<>();
        report.add("points=" + points + " corrections=" + corrections);

        UUID user = harness.createUserWithFavoriteAreas("perf", SyntheticDays.NAMED_PLACES);
        time(report, "seed points", () -> seed(user, points));
        time(report, "full rebuild (no corrections)", () -> harness.fullRebuild(user));

        List<TimelineStayEntity> all = QuarkusTransaction.requiringNew().call(() ->
                stays.find("user.id = ?1 and stayDuration > 3600 order by timestamp", user).list());
        report.add("stays=" + all.size());
        int step = Math.max(1, all.size() / corrections);
        time(report, corrections + " corrections saved", () -> {
            for (int i = 0; i < all.size() && i / step < corrections; i += step) {
                var request = label(all.get(i), "Label " + i);
                request.setPending(true);
                QuarkusTransaction.requiringNew().run(() -> edits.create(user, request));
            }
        });
        time(report, "full rebuild (with corrections)", () -> harness.fullRebuild(user));

        TimelineStayEntity old = all.get(all.size() / 3 + 1);
        var preview = time(report, "preview of a stay a third into the history", () ->
                edits.preview(user, label(old, "Measured")));
        var save = label(old, "Measured");
        save.setPreviewToken(preview.token());
        time(report, "save", () -> edits.saveGrouped(user, save));
        time(report, "correction window rebuild", () -> {
            generation.regeneratePending(user, null, "performance");
            return null;
        });
        var saved = time(report, "second save on the same stay (undo baseline)", () ->
                edits.saveGrouped(user, label(old, "Measured again")));
        time(report, "old path: rebuild from the edit to the end", () -> {
            generation.generateTimelineFromTimestamp(user, saved.affectedFrom(), null, "performance", true,
                    StreamingTimelineGenerationService.RestartPoint.LATEST_STAY);
            return null;
        });

        System.out.println("=== Correction performance ===\n" + String.join("\n", report));
        assertThat(harness.pendingRebuild(user)).isEmpty();
    }

    private void seed(UUID user, long points) {
        // Home until 08:00, a 30-minute drive, office until 17:00, a drive back, home: one point every 30 seconds.
        QuarkusTransaction.requiringNew().timeout(3600).run(() -> em.createNativeQuery("""
                INSERT INTO gps_points (device_id, user_id, coordinates, timestamp, accuracy, battery, velocity,
                                        altitude, source_type, created_at)
                SELECT 'performance', :userId,
                       ST_SetSRID(ST_MakePoint(:homeLon + r * (:officeLon - :homeLon) + (random() - 0.5) * 0.00005,
                                               :homeLat + r * (:officeLat - :homeLat) + (random() - 0.5) * 0.00005), 4326),
                       ts, 5, 90, CASE WHEN r > 0 AND r < 1 THEN 45 ELSE 0 END, 280, 'OWNTRACKS', ts
                FROM (
                    SELECT ts, CASE
                        WHEN sec < 28800 THEN 0
                        WHEN sec < 30600 THEN (sec - 28800) / 1800.0
                        WHEN sec < 61200 THEN 1
                        WHEN sec < 63000 THEN 1 - (sec - 61200) / 1800.0
                        ELSE 0 END AS r
                    FROM (
                        SELECT ts, EXTRACT(EPOCH FROM ts - date_trunc('day', ts)) AS sec
                        FROM generate_series(CAST(:start AS timestamptz),
                                             CAST(:start AS timestamptz) + (:points - 1) * interval '30 seconds',
                                             interval '30 seconds') AS ts
                    ) seconds
                ) route
                """)
                .setParameter("userId", user)
                .setParameter("start", START)
                .setParameter("points", points)
                .setParameter("homeLat", SyntheticDays.HOME.lat()).setParameter("homeLon", SyntheticDays.HOME.lon())
                .setParameter("officeLat", SyntheticDays.OFFICE.lat()).setParameter("officeLon", SyntheticDays.OFFICE.lon())
                .executeUpdate());
    }

    private static TimelineEditRequest label(TimelineStayEntity stay, String name) {
        var request = new TimelineEditRequest();
        request.setKind(TimelineEditKind.SET_VISIT_NAME);
        request.setAnchorStart(stay.getTimestamp());
        request.setAnchorEnd(stay.getTimestamp().plusSeconds(stay.getStayDuration()));
        set(request, new LinkedHashMap<>(Map.of("visitName", name)));
        return request;
    }

    private static void time(List<String> report, String step, Runnable action) {
        time(report, step, () -> {
            action.run();
            return null;
        });
    }

    private static <T> T time(List<String> report, String step, Supplier<T> action) {
        long started = System.nanoTime();
        T result = action.get();
        report.add(String.format("%-50s %10.1f s", step, (System.nanoTime() - started) / 1e9));
        return result;
    }
}
