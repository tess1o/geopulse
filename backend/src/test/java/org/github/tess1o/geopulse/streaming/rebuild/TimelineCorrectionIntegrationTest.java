package org.github.tess1o.geopulse.streaming.rebuild;

import static org.github.tess1o.geopulse.testsupport.CorrectionTestPayloads.put;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditStatus;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Issue 675 and the correction rules in docs/design/TIMELINE_CORRECTIONS.md: a fill is a statement about time and
 * always carves; a removed interval can be filled; nested fills are allowed, boundary-crossing fills are rejected
 * with an actionable conflict, and a containing fill replaces the earlier group explicitly.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineCorrectionIntegrationTest {

    @Inject TimelineReplayHarness harness;
    @Inject ActivityCorrectionService corrections;
    @Inject TimelineEditService edits;
    @Inject TimelineTripRepository trips;
    @Inject TimelineDataGapRepository gaps;

    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void setup() {
        GeocodingTestMocks.install();
    }

    @Test
    void aRouteEditKeepsOneTripAndUndoRestoresTheDetectedRoute() {
        UUID user = day.ingest(harness, "correction-route");
        TimelineTripEntity trip = onlyTrip(user);
        var start = trip.getStartPoint();
        var end = trip.getEndPoint();
        var middle = new TripReconstructionWaypointDto(49.84, 24.05);
        var request = new TimelineEditRequest();
        request.setKind(TimelineEditKind.SET_ROUTE);
        request.setAnchorStart(trip.getTimestamp());
        request.setAnchorEnd(trip.getTimestamp().plusSeconds(trip.getTripDuration()));
        request.setAffectedFrom(trip.getTimestamp());
        request.setAffectedTo(trip.getTimestamp().plusSeconds(trip.getTripDuration()));
        put(request, "waypoints", List.of(
                new TripReconstructionWaypointDto(start.getY(), start.getX()), middle,
                new TripReconstructionWaypointDto(end.getY(), end.getX())));
        var edit = edits.create(user, request);
        harness.fullRebuild(user);

        assertThat(trips.find("user.id", user).list()).hasSize(1);
        TimelineTripEntity routed = onlyTrip(user);
        assertThat(routed.getManualRoute()).hasSize(3);
        assertThat(routed.getDistanceMeters()).isPositive();

        edits.revert(user, edit.id()).orElseThrow();
        harness.fullRebuild(user);
        assertThat(trips.find("user.id", user).list()).hasSize(1);
        assertThat(onlyTrip(user).getManualRoute()).isNull();
    }

    @Test
    void aFillAppliesDuringAPartialRegeneration() {
        UUID user = day.ingest(harness, "correction-partial-regen");
        TimelineTripEntity trip = onlyTrip(user);
        Instant start = trip.getTimestamp().plusSeconds(300);
        Instant end = trip.getTimestamp().plusSeconds(600);
        var saved = corrections.save(user, request(start, end, trip.getTimestamp(),
                trip.getTimestamp().plusSeconds(trip.getTripDuration())));
        harness.regenerateFrom(user, saved.affectedFrom());
        assertThat(trips.find("user.id", user).list()).hasSize(3);
    }

    @Test
    void aFillOverADetectedTripCarvesItAndUndoRestoresTheOriginal() {
        UUID user = day.ingest(harness, "correction-carve");
        TimelineTripEntity trip = onlyTrip(user);
        Instant start = trip.getTimestamp().plusSeconds(300);
        Instant end = trip.getTimestamp().plusSeconds(600);
        Instant tripEnd = trip.getTimestamp().plusSeconds(trip.getTripDuration());

        var preview = corrections.preview(user, request(start, end, trip.getTimestamp(), tripEnd));
        assertThat(preview.warnings()).anyMatch(warning -> "CARVES_EVENT".equals(warning.code())
                && "TRIP".equals(warning.targetType()));
        assertThat(preview.token()).isNotBlank();

        var saved = corrections.save(user, request(start, end, trip.getTimestamp(), tripEnd,
                preview.token()));
        harness.fullRebuild(user);

        assertThat(trips.find("user.id", user).list()).hasSize(3);
        TimelineTripEntity manual = QuarkusTransaction.requiringNew().call(() -> trips
                .find("user.id = ?1 and timestamp = ?2", user, start).firstResult());
        assertThat(manual.getManualRoute()).isNotEmpty();

        corrections.changeStatus(user, saved.groupId(), false);
        harness.fullRebuild(user);
        assertThat(trips.find("user.id", user).list()).hasSize(1);
    }

    @Test
    void aFillInsideATimeTheUserRemovedSucceeds() {
        UUID user = day.ingest(harness, "correction-removed-gap");
        TimelineTripEntity trip = onlyTrip(user);
        Instant tripEnd = trip.getTimestamp().plusSeconds(trip.getTripDuration());

        var remove = new TimelineEditRequest();
        remove.setKind(TimelineEditKind.REMOVE_EVENT);
        remove.setAnchorStart(trip.getTimestamp());
        remove.setAnchorEnd(tripEnd);
        remove.setAffectedFrom(trip.getTimestamp());
        remove.setAffectedTo(tripEnd);
        put(remove, "resolution", "GAP");
        edits.create(user, remove);
        harness.fullRebuild(user);
        assertThat(trips.count("user.id", user)).isZero();
        assertThat(gaps.find("user.id", user).list()).isNotEmpty();

        Instant start = trip.getTimestamp().plusSeconds(300);
        Instant end = trip.getTimestamp().plusSeconds(600);
        corrections.save(user, request(start, end, trip.getTimestamp(), tripEnd));
        harness.fullRebuild(user);

        assertThat(trips.count("user.id", user)).isEqualTo(1);
        TimelineTripEntity manual = QuarkusTransaction.requiringNew().call(() -> trips
                .find("user.id = ?1 and timestamp = ?2", user, start).firstResult());
        assertThat(manual.getManualRoute()).isNotEmpty();
    }

    @Test
    void aFillNestedInsideAnEarlierFillIsAllowed() {
        UUID user = day.ingest(harness, "correction-nested");
        TimelineTripEntity trip = onlyTrip(user);
        Instant anchor = trip.getTimestamp();
        Instant tripEnd = anchor.plusSeconds(trip.getTripDuration());

        corrections.save(user, request(anchor.plusSeconds(300), anchor.plusSeconds(1200), anchor, tripEnd));
        harness.fullRebuild(user);

        Instant start = anchor.plusSeconds(500);
        Instant end = anchor.plusSeconds(700);
        var preview = corrections.preview(user, request(start, end, anchor, tripEnd));
        assertThat(preview.warnings()).anyMatch(warning -> "CARVES_CORRECTION".equals(warning.code()));
        corrections.save(user, request(start, end, anchor, tripEnd));
        harness.fullRebuild(user);
        assertThat(trips.find("user.id = ?1 and timestamp = ?2", user, start).count()).isEqualTo(1);
    }

    @Test
    void aFillThatCrossesAnEarlierFillEdgeIsRejectedWithAnActionableConflict() {
        UUID user = day.ingest(harness, "correction-partial");
        TimelineTripEntity trip = onlyTrip(user);
        Instant anchor = trip.getTimestamp();
        Instant tripEnd = anchor.plusSeconds(trip.getTripDuration());

        var first = corrections.save(user, request(anchor.plusSeconds(300), anchor.plusSeconds(900), anchor, tripEnd));
        harness.fullRebuild(user);

        Instant start = anchor.plusSeconds(800);
        Instant end = anchor.plusSeconds(1200);
        var preview = corrections.preview(user, request(start, end, anchor, tripEnd));
        assertThat(preview.warnings()).anyMatch(warning -> "PARTIAL_OVERLAP".equals(warning.code()));

        assertThatThrownBy(() -> corrections.save(user, request(start, end, anchor, tripEnd)))
                .isInstanceOf(GeoPulseException.class)
                .satisfies(error -> {
                    var conflict = (GeoPulseException) error;
                    assertThat(conflict.parameters()).containsKey("conflictingGroupId");
                    assertThat(conflict.parameters().get("conflictingGroupId")).isEqualTo(first.groupId());
                });
    }

    @Test
    void aFillContainingAnEarlierFillRequiresAnExplicitReplacementAndUndoRestoresIt() {
        UUID user = day.ingest(harness, "correction-contains");
        TimelineTripEntity trip = onlyTrip(user);
        Instant anchor = trip.getTimestamp();
        Instant tripEnd = anchor.plusSeconds(trip.getTripDuration());

        var first = corrections.save(user, request(anchor.plusSeconds(300), anchor.plusSeconds(600), anchor, tripEnd));
        harness.fullRebuild(user);

        var replacing = request(anchor, tripEnd, anchor, tripEnd);
        var preview = corrections.preview(user, replacing);
        assertThat(preview.warnings()).anyMatch(warning -> "REPLACES_CORRECTION".equals(warning.code()));
        assertThatThrownBy(() -> corrections.save(user, replacing))
                .isInstanceOf(GeoPulseException.class)
                .satisfies(error -> assertThat(((GeoPulseException) error).parameters())
                        .containsKey("replacesGroupId"));

        var replacement = corrections.save(user, new ActivityCorrectionService.Request(
                null, List.of(travel(anchor, tripEnd)), anchor, tripEnd, null, first.groupId()));
        harness.fullRebuild(user);
        assertThat(edits.list(user)).filteredOn(edit -> first.groupId().equals(edit.groupId()))
                .allSatisfy(edit -> assertThat(edit.status()).isEqualTo(org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionStatus.REPLACED));

        corrections.changeStatus(user, replacement.groupId(), false);
        harness.fullRebuild(user);
        assertThat(edits.list(user)).filteredOn(edit -> first.groupId().equals(edit.groupId()))
                .allSatisfy(edit -> assertThat(edit.status()).isEqualTo(org.github.tess1o.geopulse.streaming.edits.TimelineCorrectionStatus.IN_EFFECT));
    }

    @Test
    void aStalePreviewCannotBeSaved() {
        var track = new Track();
        var home = track.place(49.8420, 24.0310);
        var midpoint = track.place(49.8385, 24.0530);
        var office = track.place(49.8350, 24.0750);
        UUID user = harness.createUser("correction-stale");
        harness.ingest(user, track.stay(home, at("06:00"), at("07:00"), 5)
                .drive(midpoint, office, at("07:30"), at("08:00")).messages());
        harness.ingest(user, track.stay(office, at("08:10"), at("09:00"), 5)
                .drive(office, home, at("09:30"), at("10:00")).messages());
        harness.fullRebuild(user);

        var first = corrections.preview(user, new ActivityCorrectionService.Request(null,
                List.of(travel(at("07:05"), at("07:20"))), null, null, null, null));
        var second = corrections.preview(user, new ActivityCorrectionService.Request(null,
                List.of(travel(at("09:05"), at("09:20"))), null, null, null, null));
        assertThat(second.token()).isNotBlank();

        corrections.save(user, new ActivityCorrectionService.Request(null,
                List.of(travel(at("07:05"), at("07:20"))), null, null, first.token(), null));

        assertThatThrownBy(() -> corrections.save(user, new ActivityCorrectionService.Request(null,
                List.of(travel(at("09:05"), at("09:20"))), null, null, second.token(), null)))
                .isInstanceOf(GeoPulseException.class)
                .hasMessageContaining("reviewed");
    }

    private TimelineTripEntity onlyTrip(UUID user) {
        return QuarkusTransaction.requiringNew().call(() -> trips.find("user.id = ?1", user).singleResult());
    }

    private ActivityCorrectionService.Request request(Instant start, Instant end,
                                                      Instant contextStart, Instant contextEnd) {
        return request(start, end, contextStart, contextEnd, null);
    }

    private ActivityCorrectionService.Request request(Instant start, Instant end,
                                                      Instant contextStart, Instant contextEnd, String token) {
        return new ActivityCorrectionService.Request(null, List.of(travel(start, end)),
                contextStart, contextEnd, token, null);
    }

    private ActivityCorrectionService.Activity travel(Instant from, Instant to) {
        var activity = new ActivityCorrectionService.Activity();
        activity.setSegmentType("TRIP");
        activity.setStartTime(from);
        activity.setEndTime(to);
        activity.setMovementType("WALK");
        activity.setStartPlace(new ActivityCorrectionService.Place(null, null, 49.82, 24.01, "Start"));
        activity.setEndPlace(new ActivityCorrectionService.Place(null, null, 49.83, 24.03, "End"));
        activity.setWaypoints(List.of(new TripReconstructionWaypointDto(49.825, 24.025)));
        return activity;
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
