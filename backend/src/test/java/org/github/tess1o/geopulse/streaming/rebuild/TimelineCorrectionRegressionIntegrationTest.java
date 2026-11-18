package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.streaming.edits.*;
import org.github.tess1o.geopulse.streaming.edits.payload.*;
import org.github.tess1o.geopulse.streaming.model.dto.*;
import org.github.tess1o.geopulse.streaming.repository.*;
import org.github.tess1o.geopulse.streaming.service.*;
import org.github.tess1o.geopulse.testsupport.*;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.github.tess1o.geopulse.streaming.rebuild.CommuteWithStops.at;

/** Regression cases compare committed materialization, not only successful save/status responses. */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineCorrectionRegressionIntegrationTest {
    @Inject TimelineReplayHarness harness;
    @Inject ActivityCorrectionService activities;
    @Inject TimelineEditService edits;
    @Inject TimelineStayRepository stays;
    @Inject TimelineTripRepository trips;
    @Inject TimelineDataGapRepository gaps;
    @Inject RealtimeTimelineProcessor realtime;
    @Inject EntityManager em;
    @BeforeEach void mocks() { GeocodingTestMocks.install(); }

    @Test void undoingAnOuterFillPreservesItsIndependentNestedFillAndAllEffects() {
        UUID user = harness.createUser("nested-independent");
        var outer = activities.save(user, batch(stay("08:00", "09:00", "Outer")));
        harness.fullRebuild(user);
        var inner = activities.save(user, batch(stay("08:20", "08:30", "Inner")));
        harness.fullRebuild(user);
        var parent = edits.list(user).stream().filter(e -> e.groupId().equals(outer.groupId())).findFirst().orElseThrow();
        assertThat(parent.applied()).isTrue();
        assertThat(parent.effects()).hasSize(2);
        assertThat(edits.list(user).stream().filter(e -> e.groupId().equals(inner.groupId())).findFirst().orElseThrow().beforeValues()).isNull();
        activities.changeStatus(user, outer.groupId(), false);
        harness.fullRebuild(user);
        assertThat(stays.list("user.id", user)).singleElement().satisfies(row -> {
            assertThat(row.getLocationName()).isEqualTo("Inner");
            assertThat(row.getStayDuration()).isEqualTo(600);
        });
        activities.changeStatus(user, outer.groupId(), true);
        harness.fullRebuild(user);
        assertThat(stays.list("user.id", user)).hasSize(3);
    }

    @Test void replacingAGroupPreviewsItsRemovalAndRedoReproducesTheExactResult() {
        UUID user = harness.createUser("replacement-review");
        var old = activities.save(user, batch(stay("08:00", "08:20", "Old"), stay("08:40", "09:00", "Gone")));
        harness.fullRebuild(user);
        var request = new ActivityCorrectionService.Request(null, List.of(stay("08:00", "08:20", "New")), at("08:00"), at("09:00"), null, old.groupId());
        var preview = activities.preview(user, request);
        assertThat(preview.events()).noneMatch(e -> "Gone".equals(e.label()));
        var replacement = activities.save(user, new ActivityCorrectionService.Request(null, request.activities(), request.contextStart(), request.contextEnd(), preview.token(), old.groupId()));
        harness.fullRebuild(user);
        assertThat(stays.list("user.id", user)).singleElement().satisfies(row -> assertThat(row.getLocationName()).isEqualTo("New"));
        var changed = harness.snapshot(user).contentAcrossUsers();
        activities.changeStatus(user, replacement.groupId(), false); harness.fullRebuild(user);
        assertThat(stays.list("user.id", user)).hasSize(2);
        activities.changeStatus(user, replacement.groupId(), true); harness.fullRebuild(user);
        assertThat(harness.snapshot(user).contentAcrossUsers()).containsExactlyElementsOf(changed);
        activities.changeStatus(user, replacement.groupId(), false); harness.fullRebuild(user);
        assertThat(stays.list("user.id", user)).hasSize(2);
    }

    @Test void sameFieldReplacementCanBeUndoneAndRedoneWithoutLosingThePredecessor() {
        UUID user = harness.createUser("label-replacement");
        activities.save(user, batch(stay("08:00", "09:00", "Visit"))); harness.fullRebuild(user);
        var first = edits.saveGrouped(user, label("First")); harness.fullRebuild(user);
        var second = edits.saveGrouped(user, label("Second")); harness.fullRebuild(user);
        assertThat(edits.list(user)).filteredOn(edit -> edit.groupId().equals(first.groupId()))
                .singleElement().satisfies(edit -> {
                    assertThat(edit.status()).isEqualTo(TimelineCorrectionStatus.REPLACED);
                    assertThat(edit.replacedByGroupId()).isEqualTo(second.groupId());
                });
        activities.changeStatus(user, second.groupId(), false); harness.fullRebuild(user);
        assertThat(stays.list("user.id", user).getFirst().getVisitName()).isEqualTo("First");
        assertThat(edits.list(user)).filteredOn(edit -> edit.groupId().equals(first.groupId()))
                .singleElement().satisfies(edit -> assertThat(edit.status()).isEqualTo(TimelineCorrectionStatus.IN_EFFECT));
        activities.changeStatus(user, second.groupId(), true); harness.fullRebuild(user);
        assertThat(stays.list("user.id", user).getFirst().getVisitName()).isEqualTo("Second");
        assertThat(edits.list(user)).filteredOn(edit -> edit.groupId().equals(first.groupId()))
                .singleElement().satisfies(edit -> assertThat(edit.status()).isEqualTo(TimelineCorrectionStatus.REPLACED));
    }

    @Test void sameFieldReplacementUsesTheResolvedEventRatherThanAnOverlapThreshold() {
        UUID user = harness.createUser("same-field-target");
        activities.save(user, batch(stay("08:00", "09:00", "Visit"))); harness.fullRebuild(user);
        var first = edits.saveGrouped(user, label("First")); harness.fullRebuild(user);
        var second = label("Second"); second.setAnchorEnd(at("08:10"));
        edits.saveGrouped(user, second); harness.fullRebuild(user);
        assertThat(edits.list(user).stream().filter(e -> e.groupId().equals(first.groupId())).findFirst().orElseThrow().status()).isEqualTo(TimelineCorrectionStatus.REPLACED);
        assertThat(stays.list("user.id", user).getFirst().getVisitName()).isEqualTo("Second");
    }

    @Test void anEmptyLabelClearsTheLabelEmbeddedInAFillAndUndoRestoresIt() {
        UUID user = harness.createUser("embedded-label");
        var stay = stay("08:00", "09:00", "Visit"); stay.setVisitName("Original");
        activities.save(user, batch(stay)); harness.fullRebuild(user);
        var cleared = edits.saveGrouped(user, label("")); harness.fullRebuild(user);
        assertThat(stays.list("user.id", user).getFirst().getVisitName()).isNull();
        activities.changeStatus(user, cleared.groupId(), false); harness.fullRebuild(user);
        assertThat(stays.list("user.id", user).getFirst().getVisitName()).isEqualTo("Original");
    }

    @ParameterizedTest @ValueSource(strings = {"undo", "restore", "gps", "settings"})
    void previewsBecomeStaleWhenTheirInputsChangeWithoutAddingACorrection(String change) {
        UUID user = new CommuteWithStops().ingest(harness, "stale-" + change);
        var saved = activities.save(user, batch(stay("14:00", "15:00", "Other"))); harness.fullRebuild(user);
        if (change.equals("restore")) activities.changeStatus(user, saved.groupId(), false);
        var batch = batch(stay("16:00", "17:00", "Proposed"));
        var preview = activities.preview(user, batch);
        var single = label("Reviewed"); var singlePreview = edits.preview(user, single);
        switch (change) {
            case "undo" -> activities.changeStatus(user, saved.groupId(), false);
            case "restore" -> activities.changeStatus(user, saved.groupId(), true);
            case "gps" -> harness.ingest(user, new Track().stay(new double[]{49.8, 24.1}, at("18:00"), at("19:00"), 5).messages());
            case "settings" -> QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update users set timeline_preferences = jsonb_set(coalesce(timeline_preferences, '{}'::jsonb), '{staypointRadiusMeters}', '999') where id = :id").setParameter("id", user).executeUpdate());
        }
        var stale = new ActivityCorrectionService.Request(null, batch.activities(), null, null, preview.token(), null);
        assertThatThrownBy(() -> activities.save(user, stale)).isInstanceOf(GeoPulseException.class).hasMessageContaining("reviewed");
        single.setPreviewToken(singlePreview.token());
        assertThatThrownBy(() -> edits.saveGrouped(user, single)).isInstanceOf(GeoPulseException.class).hasMessageContaining("reviewed");
    }

    @Test void restoringAGroupDoesNotRestoreADetailTheUserHadAlreadyUndone() {
        UUID user = harness.createUser("redo-only-cascade");
        var group = activities.save(user, batch(stay("08:00", "09:00", "Visit"))); harness.fullRebuild(user);
        var detail = edits.saveGrouped(user, label("Already undone")); harness.fullRebuild(user);
        activities.changeStatus(user, detail.groupId(), false); harness.fullRebuild(user);
        activities.changeStatus(user, group.groupId(), false, true); harness.fullRebuild(user);
        activities.changeStatus(user, group.groupId(), true); harness.fullRebuild(user);
        assertThat(stays.list("user.id", user).getFirst().getVisitName()).isNull();
        assertThat(edits.list(user).stream().filter(e -> e.groupId().equals(detail.groupId())).findFirst().orElseThrow().status()).isEqualTo(TimelineCorrectionStatus.UNDONE);
    }

    @Test void singleAndBatchFillsEnforceTheSamePartialOverlapRule() {
        UUID user = harness.createUser("shape-parity");
        var old = activities.save(user, batch(stay("08:00", "09:00", "Old"))); harness.fullRebuild(user);
        var crossing = request(TimelineEditKind.FORCE_STAY, "08:30", "09:30", PlaceStayPayload.builder().latitude(49.8).longitude(24.1).locationName("New").build());
        assertThat(edits.fillWarnings(user, List.of(crossing), null)).anyMatch(w -> w.code().equals("PARTIAL_OVERLAP"));
        assertThatThrownBy(() -> edits.saveGrouped(user, crossing)).isInstanceOf(GeoPulseException.class);
        crossing.setReplacesGroupId(old.groupId());
        var preview = edits.preview(user, crossing); crossing.setPreviewToken(preview.token());
        edits.saveGrouped(user, crossing); harness.fullRebuild(user);
        assertThat(stays.list("user.id", user)).singleElement().satisfies(row -> assertThat(row.getLocationName()).isEqualTo("New"));
    }

    @Test void insertingAStopClipsManualGeometryAndDistanceOnBothSurvivingLegs() {
        UUID user = harness.createUser("manual-route-carve");
        var travel = new ActivityCorrectionService.Activity(); travel.setSegmentType("TRIP");
        travel.setStartTime(at("08:00")); travel.setEndTime(at("09:00")); travel.setMovementType("WALK");
        travel.setStartPlace(new ActivityCorrectionService.Place(null,null,49.8,24.0,null));
        travel.setEndPlace(new ActivityCorrectionService.Place(null,null,49.8,24.12,null));
        var outer = activities.save(user, batch(travel)); harness.fullRebuild(user);
        long distance = trips.list("user.id", user).getFirst().getDistanceMeters();
        activities.save(user, batch(stay("08:20", "08:40", "Stop"))); harness.fullRebuild(user);
        var legs = trips.find("user.id = ?1 order by timestamp", user).list();
        assertThat(legs).hasSize(2).allSatisfy(leg -> {
            assertThat(leg.getManualRoute()).hasSize(2);
            assertThat(leg.getDistanceMeters()).isCloseTo(distance / 3, within(3L));
        });
        assertThat(legs.getFirst().getEndPoint().getX()).isCloseTo(24.04, within(0.00001));
        assertThat(legs.getLast().getStartPoint().getX()).isCloseTo(24.08, within(0.00001));
        assertThat(edits.list(user).stream().filter(e -> e.groupId().equals(outer.groupId())).findFirst().orElseThrow().effects()).hasSize(2);
    }

    @Test void anIdleRealtimePassDoesNotRecreateAGapOverAResizedFinalStay() {
        UUID user = new CommuteWithStops().ingest(harness, "idle-resize");
        edits.saveGrouped(user, request(TimelineEditKind.SET_TIMES, "08:46", "12:00", new ResizePayload(at("08:46"), at("13:00"))));
        harness.fullRebuild(user);
        realtime.process(user);
        assertThat(gaps.list("user.id", user)).noneMatch(gap -> gap.getStartTime().isBefore(at("13:00")) && gap.getEndTime().isAfter(at("12:00")));
        assertThat(stays.list("user.id", user)).anyMatch(stay -> stay.getTimestamp().equals(at("08:46")) && stay.getStayDuration() == 15240);
    }

    @Test void deletingEffectsDoesNotChangeTheNextMaterialization() {
        UUID user = harness.createUser("projection-independent");
        activities.save(user, batch(stay("08:00", "09:00", "Outer"))); harness.fullRebuild(user);
        activities.save(user, batch(stay("08:20", "08:30", "Inner"))); harness.fullRebuild(user);
        var before = harness.snapshot(user).contentAcrossUsers();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("delete from timeline_correction_effects where correction_id in (select id from timeline_edits where user_id = :id)").setParameter("id", user).executeUpdate());
        harness.fullRebuild(user);
        assertThat(harness.snapshot(user).contentAcrossUsers()).containsExactlyElementsOf(before);
        assertThat(edits.list(user)).allMatch(TimelineEditDTO::applied);
    }

    @ParameterizedTest @ValueSource(strings = {"GAP", "MERGE"})
    void removingARetimedStayCanBeUndoneAndRestored(String resolution) {
        UUID user = harness.createUser("restore-retimed-stay");
        var track = new Track();
        var home = track.place(49.842, 24.031);
        var office = track.place(49.835, 24.075);
        harness.ingest(user, track.stay(home, at("06:00"), at("08:00"), 1)
                .drive(home, office, at("08:01"), at("08:45"))
                .stay(office, at("08:46"), at("12:00"), 1)
                .drive(office, home, at("12:01"), at("12:45"))
                .stay(home, at("12:46"), at("18:00"), 1).messages());
        harness.fullRebuild(user);
        var original = stays.find("user.id = ?1 and timestamp = ?2", user, at("08:46")).firstResult();
        Instant end = original.getTimestamp().plusSeconds(original.getStayDuration());
        edits.saveGrouped(user, request(TimelineEditKind.SET_TIMES, at("08:46"), end, new ResizePayload(at("08:50"), end)));
        harness.fullRebuild(user);
        var resized = harness.snapshot(user).contentAcrossUsers();
        var removed = edits.saveGrouped(user, request(TimelineEditKind.REMOVE_EVENT, at("08:50"), end, new ClearPayload(ClearPayload.RemovalResolution.valueOf(resolution))));
        harness.fullRebuild(user);
        var cleared = harness.snapshot(user).contentAcrossUsers();
        activities.changeStatus(user, removed.groupId(), false);
        harness.fullRebuild(user);
        assertThat(harness.snapshot(user).contentAcrossUsers()).containsExactlyElementsOf(resized);
        activities.changeStatus(user, removed.groupId(), true);
        harness.fullRebuild(user);
        assertThat(harness.snapshot(user).contentAcrossUsers()).containsExactlyElementsOf(cleared);
    }

    private static TimelineEditRequest label(String value) { return request(TimelineEditKind.SET_VISIT_NAME, "08:00", "09:00", new SetLabelPayload(value)); }
    private static TimelineEditRequest request(TimelineEditKind kind, String start, String end, CorrectionPayload payload) {
        return request(kind, at(start), at(end), payload);
    }
    private static TimelineEditRequest request(TimelineEditKind kind, Instant start, Instant end, CorrectionPayload payload) {
        var request = new TimelineEditRequest(); request.setKind(kind); request.setAnchorStart(start); request.setAnchorEnd(end); request.setPayload(payload); return request;
    }
    private static ActivityCorrectionService.Request batch(ActivityCorrectionService.Activity... values) { return new ActivityCorrectionService.Request(null, List.of(values), null, null); }
    private static ActivityCorrectionService.Activity stay(String start, String end, String name) {
        var activity = new ActivityCorrectionService.Activity(); activity.setSegmentType("STAY"); activity.setStartTime(at(start)); activity.setEndTime(at(end)); activity.setLatitude(49.8); activity.setLongitude(24.1); activity.setLocationName(name); return activity;
    }
}
