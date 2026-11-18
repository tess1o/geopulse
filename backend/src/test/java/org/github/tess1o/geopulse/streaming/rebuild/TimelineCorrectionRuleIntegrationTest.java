package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionStatus;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.edits.payload.ClearPayload;
import org.github.tess1o.geopulse.streaming.edits.payload.CorrectionPayload;
import org.github.tess1o.geopulse.streaming.edits.payload.ResizePayload;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.github.tess1o.geopulse.streaming.rebuild.CommuteWithStops.at;

/** The overlap, dependency and idle-pass rules hold for every path, not only the first save. */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineCorrectionRuleIntegrationTest {
    @Inject TimelineReplayHarness harness;
    @Inject ActivityCorrectionService activities;
    @Inject TimelineEditService edits;
    @Inject TimelineTripRepository trips;

    @BeforeEach
    void mocks() {
        GeocodingTestMocks.install();
    }

    /** A fill saved while an older crossing fill was undone must not be restored next to it. */
    @Test
    void restoringCannotRecreateAPartialOverlapWithAnOlderFill() {
        UUID user = harness.createUser("restore-crossing");
        var older = activities.save(user, batch(stay("10:00", "12:00", "Older")));
        harness.fullRebuild(user);
        activities.changeStatus(user, older.groupId(), false);
        harness.fullRebuild(user);
        var newer = activities.save(user, batch(stay("11:00", "13:00", "Newer")));
        harness.fullRebuild(user);
        activities.changeStatus(user, newer.groupId(), false);
        harness.fullRebuild(user);
        activities.changeStatus(user, older.groupId(), true);
        harness.fullRebuild(user);

        assertThatThrownBy(() -> activities.changeStatus(user, newer.groupId(), true))
                .isInstanceOf(GeoPulseException.class);
        assertThat(edits.list(user)).filteredOn(edit -> edit.groupId().equals(newer.groupId()))
                .allSatisfy(edit -> assertThat(edit.status()).isEqualTo(TimelineCorrectionStatus.UNDONE));
    }

    /** An idle pass only extends the ongoing gap; it never regenerates the corrected timeline again. */
    @Test
    void idlePassesDoNotRegenerateWhenACorrectionReachesPastTheLastPoint() {
        UUID user = new CommuteWithStops().ingest(harness, "idle-after-fill");
        activities.save(user, batch(stay("13:00", "14:00", "After the recording")));
        harness.fullRebuild(user);
        harness.process(user);
        var before = harness.snapshot(user);

        harness.process(user);

        var after = harness.snapshot(user);
        assertThat(after.stayIds()).as("an idle pass must not rebuild stored stays").isEqualTo(before.stayIds());
        assertThat(after.stays()).isEqualTo(before.stays());
        assertThat(after.trips()).isEqualTo(before.trips());
    }

    /** A resize depends only on corrections that produced its event, not on every correction nearby. */
    @Test
    void aResizeDoesNotDependOnAnUnrelatedNeighbouringClear() {
        UUID user = new CommuteWithStops().ingest(harness, "independent-resize");
        var trip = trips.find("user.id = ?1 order by timestamp", user).firstResult();
        Instant tripStart = trip.getTimestamp();
        Instant tripEnd = tripStart.plusSeconds(trip.getTripDuration());
        var clear = edits.saveGrouped(user, request(TimelineEditKind.REMOVE_EVENT, tripStart, tripEnd,
                new ClearPayload(ClearPayload.RemovalResolution.GAP)));
        harness.fullRebuild(user);

        var resize = request(TimelineEditKind.SET_TIMES, at("08:46"), at("12:00"),
                new ResizePayload(at("08:50"), at("12:00")));
        resize.setAffectedFrom(tripStart);
        resize.setAffectedTo(at("12:00"));
        var resized = edits.saveGrouped(user, resize);
        harness.fullRebuild(user);

        var before = resized.edits().getFirst().beforeValues();
        assertThat(before == null ? null : before.prerequisiteSequences()).isNullOrEmpty();
        activities.changeStatus(user, clear.groupId(), false);
        harness.fullRebuild(user);
        assertThat(edits.list(user)).filteredOn(edit -> edit.groupId().equals(resized.groupId()))
                .singleElement().satisfies(edit -> {
                    assertThat(edit.status()).isEqualTo(TimelineCorrectionStatus.IN_EFFECT);
                    assertThat(edit.applied()).isTrue();
                });
    }

    /** An API client does not have to describe the neighbours a removal extends or merges. */
    @Test
    void aRemovalThatExtendsANeighbourWorksWithoutAClientAffectedRange() {
        UUID user = new CommuteWithStops().ingest(harness, "server-neighbours");
        var removed = edits.saveGrouped(user, request(TimelineEditKind.REMOVE_EVENT, at("08:46"), at("12:00"),
                new ClearPayload(ClearPayload.RemovalResolution.EXTEND_PREVIOUS)));
        harness.fullRebuild(user);

        var saved = removed.edits().getFirst();
        assertThat(saved.affectedFrom()).isBefore(at("08:46"));
        assertThat(edits.list(user)).singleElement().satisfies(edit -> assertThat(edit.applied()).isTrue());
    }

    private static TimelineEditRequest request(TimelineEditKind kind, Instant start, Instant end,
                                               CorrectionPayload payload) {
        var request = new TimelineEditRequest();
        request.setKind(kind);
        request.setAnchorStart(start);
        request.setAnchorEnd(end);
        request.setPayload(payload);
        return request;
    }

    private static ActivityCorrectionService.Request batch(ActivityCorrectionService.Activity... values) {
        return new ActivityCorrectionService.Request(null, List.of(values), null, null);
    }

    private static ActivityCorrectionService.Activity stay(String start, String end, String name) {
        var activity = new ActivityCorrectionService.Activity();
        activity.setSegmentType("STAY");
        activity.setStartTime(at(start));
        activity.setEndTime(at(end));
        activity.setLatitude(49.8);
        activity.setLongitude(24.1);
        activity.setLocationName(name);
        return activity;
    }
}
