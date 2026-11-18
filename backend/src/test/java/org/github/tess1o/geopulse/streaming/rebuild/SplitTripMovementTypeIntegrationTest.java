package org.github.tess1o.geopulse.streaming.rebuild;

import static org.github.tess1o.geopulse.testsupport.CorrectionTestPayloads.put;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A trip's movement type set by hand belongs to the time the user travelled. Splitting the trip must keep it on both
 * legs, and undoing the split must give the whole trip back what it had: a type set on one leg belongs to that leg.
 * Splits are ordinary FORCE_STAY corrections in the unified model.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class SplitTripMovementTypeIntegrationTest {

    @Inject TimelineReplayHarness harness;
    @Inject TimelineEditService edits;
    @Inject ActivityCorrectionService corrections;
    @Inject TimelineTripRepository trips;

    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void splittingATripKeepsItsManualMovementTypeOnBothLegs() {
        UUID userId = day.ingest(harness, "split-keeps-type");
        TimelineTripEntity commute = onlyTrip(userId);
        setMovementType(userId, commute, "TRAIN");
        harness.fullRebuild(userId);
        assertThat(trips(userId)).as("the trip before the split").containsExactly("TRAIN");

        splitAtBakery(userId);
        assertThat(trips(userId)).as("legs right after the split").containsExactly("TRAIN", "TRAIN");
        harness.fullRebuild(userId);
        assertThat(trips(userId)).as("legs after a full rebuild").containsExactly("TRAIN", "TRAIN");
    }

    @Test
    void undoingASplitRestoresTheTripsManualMovementType() {
        UUID userId = day.ingest(harness, "split-undo-type");
        TimelineTripEntity commute = onlyTrip(userId);
        setMovementType(userId, commute, "TRAIN");
        harness.fullRebuild(userId);

        var split = splitAtBakery(userId);
        harness.fullRebuild(userId);
        assertThat(trips(userId)).containsExactly("TRAIN", "TRAIN");

        corrections.changeStatus(userId, split.groupId(), false);
        harness.fullRebuild(userId);
        assertThat(trips(userId)).as("the trip after undoing the split").containsExactly("TRAIN");
    }

    @Test
    void undoingASplitDoesNotSpreadALegsMovementTypeToTheWholeTrip() {
        UUID userId = day.ingest(harness, "split-undo-leg-type");
        String automatic = trips(userId).getFirst();

        var split = splitAtBakery(userId);
        harness.fullRebuild(userId);
        var secondLeg = onlyTripRows(userId).getLast();
        setMovementType(userId, secondLeg, "BICYCLE");
        harness.fullRebuild(userId);

        corrections.changeStatus(userId, split.groupId(), false, true);
        harness.fullRebuild(userId);
        assertThat(trips(userId)).as("the trip after undoing the split").containsExactly(automatic);
    }

    private ActivityCorrectionService.Saved splitAtBakery(UUID userId) {
        var stay = new ActivityCorrectionService.Activity();
        stay.setSegmentType("STAY");
        stay.setStartTime(at("08:16"));
        stay.setEndTime(at("08:20"));
        stay.setLatitude(day.bakery[0]);
        stay.setLongitude(day.bakery[1]);
        stay.setLocationName("Bakery");
        var saved = corrections.save(userId, new ActivityCorrectionService.Request(
                null, List.of(stay), null, null));
        harness.regenerateFrom(userId, saved.affectedFrom());
        return saved;
    }

    private void setMovementType(UUID userId, TimelineTripEntity trip, String movementType) {
        var request = new TimelineEditRequest();
        request.setKind(TimelineEditKind.SET_MOVEMENT_TYPE);
        request.setAnchorStart(trip.getTimestamp());
        request.setAnchorEnd(trip.getTimestamp().plusSeconds(trip.getTripDuration()));
        request.setAffectedFrom(trip.getTimestamp());
        request.setAffectedTo(trip.getTimestamp().plusSeconds(trip.getTripDuration()));
        put(request, "sourceType", "TRIP");
        put(request, "sourceStartLatitude", trip.getStartPoint().getY());
        put(request, "sourceStartLongitude", trip.getStartPoint().getX());
        put(request, "sourceEndLatitude", trip.getEndPoint().getY());
        put(request, "sourceEndLongitude", trip.getEndPoint().getX());
        put(request, "movementType", movementType);
        edits.create(userId, request);
    }

    private TimelineTripEntity onlyTrip(UUID userId) {
        return QuarkusTransaction.requiringNew().call(() ->
                trips.find("user.id = ?1 order by timestamp", userId).firstResult());
    }

    private List<TimelineTripEntity> onlyTripRows(UUID userId) {
        return QuarkusTransaction.requiringNew().call(() ->
                trips.find("user.id = ?1 order by timestamp", userId).list());
    }

    /** The movement types of the user's trips, in order. */
    private List<String> trips(UUID userId) {
        return onlyTripRows(userId).stream().map(TimelineTripEntity::getMovementType).toList();
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
