package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.service.TripMovementTypeOverrideService;
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
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class SplitTripMovementTypeIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    ManualEditsSupport edits;

    @Inject
    TripMovementTypeOverrideService movementTypeOverrideService;

    private final CommuteWithStops day = new CommuteWithStops();

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void splittingATripKeepsItsManualMovementTypeOnBothLegs() {
        UUID userId = day.ingest(harness, "split-keeps-type");
        TimelineTripEntity commute = edits.tripCovering(userId, at("08:16"), at("08:20"));
        movementTypeOverrideService.setManualMovementType(userId, commute.getId(), TripType.TRAIN);

        edits.split(userId, at("08:16"), at("08:20"), day.bakery, "Bakery");

        assertThat(trips(userId)).as("legs right after the split").containsExactly("TRAIN", "TRAIN");
        harness.fullRebuild(userId);
        assertThat(trips(userId)).as("legs after a full rebuild").containsExactly("TRAIN", "TRAIN");
    }

    @Test
    void undoingASplitRestoresTheTripsManualMovementType() {
        UUID userId = day.ingest(harness, "split-undo-type");
        TimelineTripEntity commute = edits.tripCovering(userId, at("08:16"), at("08:20"));
        movementTypeOverrideService.setManualMovementType(userId, commute.getId(), TripType.TRAIN);
        Long split = edits.split(userId, at("08:16"), at("08:20"), day.bakery, "Bakery");

        edits.undoSplit(userId, split);

        assertThat(trips(userId)).as("the trip after undoing the split").containsExactly("TRAIN");
    }

    @Test
    void undoingASplitDoesNotSpreadALegsMovementTypeToTheWholeTrip() {
        UUID userId = day.ingest(harness, "split-undo-leg-type");
        String automatic = trips(userId).getFirst();
        Long split = edits.split(userId, at("08:16"), at("08:20"), day.bakery, "Bakery");
        // The second, longer leg is set to bicycle.
        TimelineTripEntity secondLeg = edits.tripCovering(userId, at("08:27"), at("08:31"));
        movementTypeOverrideService.setManualMovementType(userId, secondLeg.getId(), TripType.BICYCLE);

        edits.undoSplit(userId, split);

        assertThat(trips(userId)).as("the trip after undoing the split").containsExactly(automatic);
    }

    /** The movement types of the user's trips, in order. */
    private List<String> trips(UUID userId) {
        return harness.snapshot(userId).trips().stream()
                .map(trip -> trip.split(" ")[4])
                .toList();
    }

    private static Instant at(String time) {
        return CommuteWithStops.at(time);
    }
}
