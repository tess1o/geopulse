package org.github.tess1o.geopulse.streaming.rebuild;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.WorkloadMetricsTestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.CorrectionTestPayloads.set;

/**
 * A correction changes no GPS point, so its rebuild covers only the period it affects. Whatever that saves, the
 * timeline must be exactly what a full rebuild produces: a twin user receives the same recording and the same
 * corrections and is fully rebuilt after every step, and both timelines are compared row by row.
 */
@QuarkusTest
@TestProfile(WorkloadMetricsTestProfile.class)
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class CorrectionWindowRebuildIntegrationTest {

    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 4, 6);
    private static final int DAYS = 21;
    /** The edited day, in the middle of three weeks. */
    private static final Instant EDITED_DAY = FIRST_DAY.plusDays(9).atStartOfDay(SyntheticDays.ZONE).toInstant();

    @Inject TimelineReplayHarness harness;
    @Inject TimelineEditService edits;
    @Inject ActivityCorrectionService corrections;
    @Inject StreamingTimelineGenerationService generation;
    @Inject TimelineStayRepository stays;
    @Inject TimelineTripRepository trips;
    @Inject MeterRegistry meters;

    enum Correction {
        VISIT_NAME, MOVEMENT_TYPE, RESIZE_STAY, SPLIT_STAY, REMOVE_EXTEND_PREVIOUS, REMOVE_AS_GAP, FILL_STAY
    }

    private record Twins(UUID windowed, UUID reference, List<OwnTracksLocationMessage> recording) {
    }

    @BeforeEach
    void mocks() {
        GeocodingTestMocks.install();
    }

    @ParameterizedTest
    @EnumSource(Correction.class)
    void eachCorrectionRebuildsOnlyItsPeriodAndMatchesAFullRebuild(Correction correction) {
        Twins twins = twins("window-" + correction.name().toLowerCase(), 31);
        Map<Long, Instant> farRows = farFromEditedDay(twins.windowed());

        double windows = windowRuns("window");
        double points = windowPoints();
        var saved = saveOnBoth(twins, request(correction, twins.windowed()));
        rebuild(twins);

        assertSameTimeline(twins, correction + " saved");
        assertThat(farFromEditedDay(twins.windowed())).as("rows away from the corrected period are kept")
                .isEqualTo(farRows);
        assertThat(windowRuns("window") - windows).as("rebuilt as a window").isEqualTo(1);
        assertThat(windowPoints() - points).as("points read of %d recorded", twins.recording().size())
                .isPositive().isLessThan(twins.recording().size() / 4.0);

        undoOnBoth(twins, saved, false);
        rebuild(twins);
        assertSameTimeline(twins, correction + " undone");

        undoOnBoth(twins, saved, true);
        rebuild(twins);
        assertSameTimeline(twins, correction + " restored");
        assertThat(farFromEditedDay(twins.windowed())).isEqualTo(farRows);
    }

    @Test
    void pointsArrivingAfterThePeriodAreRebuiltAfterTheWindow() {
        Twins twins = twins("window-live", 32);
        saveOnBoth(twins, request(Correction.VISIT_NAME, twins.windowed()));
        var live = live(twins.recording().getLast(), 300);
        harness.ingest(twins.windowed(), List.of(live));
        harness.ingest(twins.reference(), List.of(live));

        double windows = windowRuns("window");
        generation.regeneratePending(twins.windowed(), null, "test");
        assertThat(windowRuns("window") - windows).isEqualTo(1);
        assertThat(harness.pendingRebuild(twins.windowed())).as("the new points still wait for their pass").isPresent();
        generation.regeneratePending(twins.windowed(), null, "test");
        assertThat(harness.pendingRebuild(twins.windowed())).isEmpty();
        harness.fullRebuild(twins.reference());

        assertSameTimeline(twins, "label and a live point");
    }

    @Test
    void aLateUploadInsideThePeriodGetsTheRegularPass() {
        Twins twins = twins("window-backfill", 33);
        TimelineStayEntity stay = longStayOn(twins.windowed(), EDITED_DAY);
        saveOnBoth(twins, request(Correction.VISIT_NAME, twins.windowed()));
        var late = lateAt(twins.recording(), stay.getTimestamp().plusSeconds(stay.getStayDuration() / 2));
        harness.ingest(twins.windowed(), List.of(late));
        harness.ingest(twins.reference(), List.of(late));

        double fallbacks = windowRuns("gps_changed_in_window");
        rebuild(twins);

        assertThat(windowRuns("gps_changed_in_window") - fallbacks).isEqualTo(1);
        assertSameTimeline(twins, "label and a late point inside it");
    }

    @Test
    void aCorrectionOfTheLatestStayGetsTheRegularPass() {
        Twins twins = twins("window-tail", 34);
        TimelineStayEntity last = QuarkusTransaction.requiringNew().call(() ->
                stays.find("user.id = ?1 order by timestamp desc", twins.windowed()).firstResult());
        double fallbacks = windowRuns("end_of_timeline");
        saveOnBoth(twins, user -> label(last, "Latest"));
        rebuild(twins);

        assertThat(windowRuns("end_of_timeline") - fallbacks).isEqualTo(1);
        assertSameTimeline(twins, "label on the latest stay");
    }

    @Test
    void twoCorrectionsFarApartAreBothRebuilt() {
        Twins twins = twins("window-two", 35);
        TimelineStayEntity early = longStayOn(twins.windowed(), EDITED_DAY.minus(Duration.ofDays(5)));
        TimelineStayEntity late = longStayOn(twins.windowed(), EDITED_DAY.plus(Duration.ofDays(5)));
        saveOnBoth(twins, user -> label(early, "Early"));
        saveOnBoth(twins, user -> label(late, "Late"));
        rebuild(twins);

        assertSameTimeline(twins, "two labels ten days apart");
        assertThat(harness.pendingRebuild(twins.windowed())).isEmpty();
    }

    private Twins twins(String prefix, long seed) {
        List<OwnTracksLocationMessage> recording = SyntheticDays.generate(seed, FIRST_DAY, DAYS);
        UUID windowed = harness.createUserWithFavoriteAreas(prefix + "-a", SyntheticDays.NAMED_PLACES);
        UUID reference = harness.createUserWithFavoriteAreas(prefix + "-b", SyntheticDays.NAMED_PLACES);
        for (UUID user : List.of(windowed, reference)) {
            harness.seedRecording(user, recording);
            harness.fullRebuild(user);
        }
        Twins twins = new Twins(windowed, reference, recording);
        assertSameTimeline(twins, "recorded");
        return twins;
    }

    /** The windowed user takes the correction rebuild a save schedules; the reference user is fully rebuilt. */
    private void rebuild(Twins twins) {
        generation.regeneratePending(twins.windowed(), null, "test");
        harness.fullRebuild(twins.reference());
        assertThat(harness.pendingRebuild(twins.windowed())).isEmpty();
    }

    private void assertSameTimeline(Twins twins, String step) {
        List<String> windowed = harness.snapshot(twins.windowed()).contentAcrossUsers();
        List<String> reference = harness.snapshot(twins.reference()).contentAcrossUsers();
        assertThat(windowed)
                .as("%s: window rebuild differs from a full rebuild.%nwindow:%n  %s%nfull:%n  %s", step,
                        String.join("\n  ", windowed), String.join("\n  ", reference))
                .containsExactlyElementsOf(reference);
    }

    /** Both users' timelines are identical, so a request built from one user's rows fits the other's as well. */
    private List<ActivityCorrectionService.Saved> saveOnBoth(Twins twins, Function<UUID, TimelineEditRequest> request) {
        return List.of(edits.saveGrouped(twins.windowed(), request.apply(twins.windowed())),
                edits.saveGrouped(twins.reference(), request.apply(twins.windowed())));
    }

    private void undoOnBoth(Twins twins, List<ActivityCorrectionService.Saved> saved, boolean restore) {
        corrections.changeStatus(twins.windowed(), saved.getFirst().groupId(), restore);
        corrections.changeStatus(twins.reference(), saved.getLast().groupId(), restore);
    }

    private Function<UUID, TimelineEditRequest> request(Correction correction, UUID user) {
        TimelineStayEntity stay = longStayOn(user, EDITED_DAY);
        TimelineTripEntity trip = QuarkusTransaction.requiringNew().call(() -> trips
                .find("user.id = ?1 and timestamp >= ?2 and tripDuration > 300 order by timestamp", user,
                        stay.getTimestamp().plusSeconds(stay.getStayDuration()))
                .firstResult());
        Instant stayStart = stay.getTimestamp();
        Instant stayEnd = stayStart.plusSeconds(stay.getStayDuration());
        Instant tripStart = trip.getTimestamp();
        Instant tripEnd = tripStart.plusSeconds(trip.getTripDuration());
        return ignored -> switch (correction) {
            case VISIT_NAME -> label(stay, "Window label");
            case MOVEMENT_TYPE -> edit(TimelineEditKind.SET_MOVEMENT_TYPE, tripStart, tripEnd,
                    Map.of("movementType", "TRAIN"));
            case RESIZE_STAY -> edit(TimelineEditKind.SET_TIMES, stayStart, stayEnd, Map.of(
                    "start", stayStart.plusSeconds(600).toString(), "end", stayEnd.minusSeconds(300).toString()));
            case SPLIT_STAY -> edit(TimelineEditKind.SPLIT_STAY, stayStart, stayEnd, Map.of(
                    "splitAt", stayStart.plusSeconds(stay.getStayDuration() / 2).toString()));
            case REMOVE_EXTEND_PREVIOUS -> edit(TimelineEditKind.REMOVE_EVENT, tripStart, tripEnd,
                    Map.of("resolution", "EXTEND_PREVIOUS"));
            case REMOVE_AS_GAP -> edit(TimelineEditKind.REMOVE_EVENT, tripStart, tripEnd,
                    Map.of("resolution", "GAP"));
            case FILL_STAY -> edit(TimelineEditKind.FORCE_STAY, tripStart.plus(Duration.between(tripStart, tripEnd).dividedBy(3)),
                    tripEnd.minus(Duration.between(tripStart, tripEnd).dividedBy(3)), Map.of(
                            "latitude", SyntheticDays.PARK_GATE.lat(), "longitude", SyntheticDays.PARK_GATE.lon(),
                            "locationName", "Window stop"));
        };
    }

    private TimelineStayEntity longStayOn(UUID user, Instant day) {
        return QuarkusTransaction.requiringNew().call(() ->
                stays.find("user.id = ?1 and timestamp >= ?2 and stayDuration > 3600 order by timestamp", user,
                                day.plus(Duration.ofHours(6)))
                        .firstResult());
    }

    /** Stay ids by start, for stays more than three days from the edited day. */
    private Map<Long, Instant> farFromEditedDay(UUID user) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Map<Long, Instant> rows = new LinkedHashMap<>();
            stays.find("user.id = ?1 and (timestamp < ?2 or timestamp > ?3) order by timestamp", user,
                            EDITED_DAY.minus(Duration.ofDays(3)), EDITED_DAY.plus(Duration.ofDays(4)))
                    .stream().forEach(stay -> rows.put(stay.getId(), stay.getTimestamp()));
            return rows;
        });
    }

    private static TimelineEditRequest label(TimelineStayEntity stay, String name) {
        return edit(TimelineEditKind.SET_VISIT_NAME, stay.getTimestamp(),
                stay.getTimestamp().plusSeconds(stay.getStayDuration()), Map.of("visitName", name));
    }

    private static TimelineEditRequest edit(TimelineEditKind kind, Instant start, Instant end, Map<String, Object> payload) {
        var request = new TimelineEditRequest();
        request.setKind(kind);
        request.setAnchorStart(start);
        request.setAnchorEnd(end);
        set(request, new LinkedHashMap<>(payload));
        return request;
    }

    private static OwnTracksLocationMessage live(OwnTracksLocationMessage last, long seconds) {
        return OwnTracksLocationMessage.builder().type("location").tid("synthetic")
                .tst(last.getTst() + seconds).createdAt(last.getTst() + seconds)
                .lat(last.getLat()).lon(last.getLon()).acc(5.0).vel(0.0).build();
    }

    private static OwnTracksLocationMessage lateAt(List<OwnTracksLocationMessage> recording, Instant time) {
        OwnTracksLocationMessage nearest = recording.stream()
                .filter(message -> message.getTst() <= time.getEpochSecond())
                .reduce((first, second) -> second).orElseThrow();
        return OwnTracksLocationMessage.builder().type("location").tid("synthetic")
                .tst(time.getEpochSecond() + 1).createdAt(Instant.now().getEpochSecond())
                .lat(nearest.getLat()).lon(nearest.getLon()).acc(5.0).vel(0.0).build();
    }

    private double windowRuns(String result) {
        Counter counter = meters.find(StreamingTimelineGenerationService.CORRECTION_WINDOW_RUNS)
                .tag("result", result).counter();
        return counter == null ? 0 : counter.count();
    }

    private double windowPoints() {
        Counter counter = meters.find(StreamingTimelineGenerationService.CORRECTION_WINDOW_GPS_POINTS).counter();
        return counter == null ? 0 : counter.count();
    }
}
