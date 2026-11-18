package org.github.tess1o.geopulse.streaming.rebuild;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.github.tess1o.geopulse.testsupport.WorkloadMetricsTestProfile;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.shared.api.ApiErrorCode;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditPreviewDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.github.tess1o.geopulse.testsupport.CorrectionTestPayloads.set;

/**
 * Reviewing a correction runs inside the request. While a phone is tracking, a rebuild is almost always pending for
 * the newest points; a review of an older event must not read the recording up to today for it, and a point arriving
 * after the reviewed period must not invalidate the review.
 */
@QuarkusTest
@TestProfile(WorkloadMetricsTestProfile.class)
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class CorrectionPreviewScopeIntegrationTest {

    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 3, 2);
    private static final int DAYS = 21;
    private static final Instant THIRD_DAY = FIRST_DAY.plusDays(2).atStartOfDay(SyntheticDays.ZONE).toInstant();

    @Inject TimelineReplayHarness harness;
    @Inject TimelineEditService edits;
    @Inject TimelineStayRepository stays;
    @Inject MeterRegistry meters;

    @BeforeEach
    void mocks() {
        GeocodingTestMocks.install();
    }

    @Test
    void reviewOfAnOldEventWhileTrackingReadsNoPointsAndMatchesTheSavedResult() {
        List<OwnTracksLocationMessage> recording = SyntheticDays.generate(21, FIRST_DAY, DAYS);
        UUID user = recordedUser("review-live", recording);
        harness.ingest(user, List.of(liveAfter(recording.getLast(), 120)));
        assertThat(harness.pendingRebuild(user)).as("a tracking phone leaves a rebuild pending").isPresent();

        TimelineStayEntity stay = firstStayFrom(user, THIRD_DAY);
        double before = reviewedPoints();
        TimelineEditPreviewDTO preview = edits.preview(user, label(stay, "Reviewed while tracking"));

        assertThat(reviewedPoints() - before).as("GPS points read to review a three-week-old stay").isZero();
        assertThat(preview.events()).anySatisfy(event -> assertThat(event.label()).isEqualTo("Reviewed while tracking"));

        var save = label(stay, "Reviewed while tracking");
        save.setPreviewToken(preview.token());
        var saved = edits.saveGrouped(user, save);
        harness.regenerateFrom(user, saved.affectedFrom());
        assertThat(storedLabels(user)).contains("Reviewed while tracking");
    }

    @Test
    void reviewWithAChangeInsideThePeriodReadsOnlyThePeriodNotTheWholeRecording() {
        List<OwnTracksLocationMessage> recording = SyntheticDays.generate(22, FIRST_DAY, DAYS);
        UUID user = recordedUser("review-backfill", recording);
        TimelineStayEntity stay = firstStayFrom(user, THIRD_DAY);
        // A late upload inside the reviewed stay makes the stored timeline of that period stale.
        harness.ingest(user, List.of(liveAt(recording, stay.getTimestamp().plusSeconds(stay.getStayDuration() / 2))));
        assertThat(harness.pendingRebuild(user)).isPresent();

        double before = reviewedPoints();
        TimelineEditPreviewDTO preview = edits.preview(user, label(stay, "Reviewed after backfill"));
        double read = reviewedPoints() - before;

        assertThat(preview.events()).anySatisfy(event -> assertThat(event.label()).isEqualTo("Reviewed after backfill"));
        assertThat(read).as("points read of %d recorded", recording.size()).isPositive()
                .isLessThan(recording.size() / 4.0);
    }

    @Test
    void aPointRecordedAfterTheReviewedPeriodDoesNotInvalidateTheReview() {
        List<OwnTracksLocationMessage> recording = SyntheticDays.generate(23, FIRST_DAY, DAYS);
        UUID user = recordedUser("review-token-live", recording);
        TimelineStayEntity stay = firstStayFrom(user, THIRD_DAY);
        TimelineEditPreviewDTO preview = edits.preview(user, label(stay, "Saved after a new point"));

        harness.ingest(user, List.of(liveAfter(recording.getLast(), 120)));

        var save = label(stay, "Saved after a new point");
        save.setPreviewToken(preview.token());
        var saved = edits.saveGrouped(user, save);
        assertThat(saved.edits()).hasSize(1);
    }

    @Test
    void aCorrectionInsideTheReviewedPeriodStillInvalidatesTheReview() {
        List<OwnTracksLocationMessage> recording = SyntheticDays.generate(24, FIRST_DAY, DAYS);
        UUID user = recordedUser("review-token-stale", recording);
        TimelineStayEntity stay = firstStayFrom(user, THIRD_DAY);
        TimelineEditPreviewDTO preview = edits.preview(user, label(stay, "Reviewed first"));

        edits.saveGrouped(user, label(stay, "Saved elsewhere meanwhile"));

        var save = label(stay, "Reviewed first");
        save.setPreviewToken(preview.token());
        assertThatThrownBy(() -> edits.saveGrouped(user, save))
                .isInstanceOfSatisfying(GeoPulseException.class,
                        error -> assertThat(error.code()).isEqualTo(ApiErrorCode.TIMELINE_PREVIEW_STALE));
    }

    private UUID recordedUser(String prefix, List<OwnTracksLocationMessage> recording) {
        UUID user = harness.createUserWithFavoriteAreas(prefix, SyntheticDays.NAMED_PLACES);
        harness.ingest(user, recording);
        harness.fullRebuild(user);
        assertThat(harness.pendingRebuild(user)).isEmpty();
        return user;
    }

    private TimelineStayEntity firstStayFrom(UUID user, Instant from) {
        return QuarkusTransaction.requiringNew().call(() ->
                stays.find("user.id = ?1 and timestamp >= ?2 and stayDuration > 3600 order by timestamp", user, from)
                        .firstResult());
    }

    private List<String> storedLabels(UUID user) {
        return QuarkusTransaction.requiringNew().call(() -> stays.find("user.id = ?1 and visitName is not null", user)
                .stream().map(TimelineStayEntity::getVisitName).toList());
    }

    private static TimelineEditRequest label(TimelineStayEntity stay, String name) {
        var request = new TimelineEditRequest();
        request.setKind(TimelineEditKind.SET_VISIT_NAME);
        request.setAnchorStart(stay.getTimestamp());
        request.setAnchorEnd(stay.getTimestamp().plusSeconds(stay.getStayDuration()));
        set(request, new LinkedHashMap<>(Map.of("visitName", name)));
        return request;
    }

    private static OwnTracksLocationMessage liveAfter(OwnTracksLocationMessage last, long seconds) {
        return OwnTracksLocationMessage.builder().type("location").tid("synthetic")
                .tst(last.getTst() + seconds).createdAt(last.getTst() + seconds)
                .lat(last.getLat()).lon(last.getLon()).acc(5.0).vel(0.0).build();
    }

    /** A late point at {@code time}, placed where the recording was at that moment. */
    private static OwnTracksLocationMessage liveAt(List<OwnTracksLocationMessage> recording, Instant time) {
        OwnTracksLocationMessage nearest = recording.stream()
                .filter(message -> message.getTst() <= time.getEpochSecond())
                .reduce((first, second) -> second).orElseThrow();
        return OwnTracksLocationMessage.builder().type("location").tid("synthetic")
                .tst(time.getEpochSecond() + 1).createdAt(Instant.now().getEpochSecond())
                .lat(nearest.getLat()).lon(nearest.getLon()).acc(5.0).vel(0.0).build();
    }

    private double reviewedPoints() {
        Counter counter = meters.find(StreamingTimelineGenerationService.CORRECTION_REVIEW_GPS_POINTS).counter();
        return counter == null ? 0 : counter.count();
    }
}
