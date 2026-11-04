package org.github.tess1o.geopulse.streaming.edits;

import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.model.shared.MovementTypeSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TimelineEditApplierTest {

    private static final Instant ZERO = Instant.parse("2026-10-01T10:00:00Z");

    private final TimelineEditApplier applier = new TimelineEditApplier();

    @Test
    void reconstructedShortStayIsExactEvenWithoutAutomaticEvents() {
        var correction = edit(1, TimelineEditKind.FORCE_STAY, 0, 3,
                Map.of("latitude", 50.0, "longitude", 30.0, "reconstruction", true));
        var result = applier.apply(List.of(), List.of(correction));
        assertThat(result.events()).singleElement().isInstanceOfSatisfying(Stay.class, stay -> {
            assertThat(stay.getStartTime()).isEqualTo(ZERO);
            assertThat(stay.getDuration()).isEqualTo(Duration.ofMinutes(3));
            assertThat(stay.getExplanation().getStart().code()).isEqualTo("MANUAL_RECONSTRUCTION");
        });
        assertThat(result.appliedEditIds()).containsExactly(1L);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void deletedPlaceReferencesBecomeHistoricalSnapshots(boolean favorite) {
        var stay = Stay.builder().startTime(Instant.parse("2026-09-15T08:00:00Z")).duration(Duration.ofHours(1))
                .latitude(50.0).longitude(30.0).locationName("Preserved place").visitName("Independent label")
                .favoriteId(favorite ? 99L : null).geocodingId(favorite ? null : 99L).build();
        var converter = new org.github.tess1o.geopulse.streaming.service.converters.StreamingTimelineConverter(null);
        var row = converter.convertStayToEntityWithBatchData(stay, null, Map.of(), Map.of());
        assertThat(row.getLocationSource()).isEqualTo(org.github.tess1o.geopulse.streaming.model.domain.LocationSource.HISTORICAL);
        assertThat(row.getFavoriteLocation()).isNull(); assertThat(row.getGeocodingLocation()).isNull();
        assertThat(row.getLocationName()).isEqualTo("Preserved place");
        assertThat(row.getVisitName()).isEqualTo("Independent label");
        assertThat(row.getLocation().getX()).isEqualTo(30.0); assertThat(row.getLocation().getY()).isEqualTo(50.0);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {500, 501})
    void visitNameLengthBoundary(int length) {
        var source = stay(0, 60, "Home");
        var command = edit(1, TimelineEditKind.SET_VISIT_NAME, 0, 60, Map.of("visitName", "x".repeat(length)));
        if (length == 500) {
            var result = applier.apply(List.of(source), List.of(command));
            assertThat(((Stay) result.events().getFirst()).getVisitName()).isEqualTo("x".repeat(length));
            assertThat(result.appliedEditIds()).containsExactly(1L);
        } else {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> applier.apply(List.of(source), List.of(command)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Visit name must be at most 500 characters");
        }
        assertThat(source.getVisitName()).isNull();
    }

    @Test
    void foldsStructuralThenAttributeEditsOverTheResult() {
        Trip source = trip(0, 60, TripType.CAR);
        TimelineEditCommand forceStay = edit(1, TimelineEditKind.FORCE_STAY, 20, 40, Map.of(
                "latitude", 50.45,
                "longitude", 30.52,
                "locationName", "Cafe"));
        TimelineEditCommand name = edit(2, TimelineEditKind.SET_VISIT_NAME, 20, 40,
                Map.of("visitName", "Lunch"));

        TimelineEditResult result = applier.apply(List.of(source), List.of(name, forceStay));

        assertThat(result.events()).hasSize(3);
        assertThat(result.events().get(1)).isInstanceOfSatisfying(Stay.class, stay -> {
            assertThat(stay.getLocationName()).isEqualTo("Cafe");
            assertThat(stay.getVisitName()).isEqualTo("Lunch");
        });
        assertThat(result.appliedEditIds()).containsExactly(1L, 2L);
    }

    @Test
    void changesMovementAndPlaceWithoutChangingEventShape() {
        Trip trip = trip(0, 20, TripType.CAR);
        Stay stay = stay(20, 20, "Old");

        TimelineEditResult result = applier.apply(List.of(trip, stay), List.of(
                edit(1, TimelineEditKind.SET_MOVEMENT_TYPE, 0, 20, Map.of("movementType", "TRAIN")),
                edit(2, TimelineEditKind.SET_PLACE, 20, 40, Map.of(
                        "locationName", "New", "latitude", 1.5, "longitude", 2.5))));

        assertThat(((Trip) result.events().get(0)).getTripType()).isEqualTo(TripType.TRAIN);
        assertThat(((Trip) result.events().get(0)).getMovementTypeSource()).isEqualTo(MovementTypeSource.MANUAL);
        assertThat(trip.getMovementTypeSource()).isEqualTo(MovementTypeSource.AUTO);
        assertThat((Stay) result.events().get(1)).satisfies(changed -> {
            assertThat(changed.getLocationName()).isEqualTo("New");
            assertThat(changed.getLatitude()).isEqualTo(1.5);
            assertThat(changed.getLongitude()).isEqualTo(2.5);
        });
    }

    @Test
    void splittingAManuallyClassifiedTripKeepsItsSourceOnBothParts() {
        Trip source = trip(0, 60, TripType.WALK);
        source.setMovementTypeSource(MovementTypeSource.MANUAL);
        TimelineEditResult result = applier.apply(List.of(source), List.of(
                edit(1, TimelineEditKind.FORCE_STAY, 20, 40, Map.of("locationName", "Cafe"))));
        assertThat(result.events().stream().filter(Trip.class::isInstance).map(Trip.class::cast))
                .hasSize(2).allSatisfy(trip -> assertThat(trip.getMovementTypeSource()).isEqualTo(MovementTypeSource.MANUAL));
    }

    @Test
    void mergingKeepsManualMovementWhenTheLaterTripWasEdited() {
        Trip first = trip(0, 20, TripType.WALK);
        Trip last = trip(20, 20, TripType.WALK);
        last.setMovementTypeSource(MovementTypeSource.MANUAL);
        TimelineEditResult result = applier.apply(List.of(first, last), List.of(
                edit(1, TimelineEditKind.MERGE, 0, 20, Map.of("direction", "NEXT"))));
        assertThat(result.events()).singleElement().isInstanceOfSatisfying(Trip.class,
                trip -> assertThat(trip.getMovementTypeSource()).isEqualTo(MovementTypeSource.MANUAL));
    }

    @Test
    void movingBoundaryUpdatesBothAdjacentEvents() {
        Stay first = stay(0, 20, "A");
        Trip second = trip(20, 20, TripType.CAR);

        TimelineEditResult result = applier.apply(List.of(first, second), List.of(
                edit(1, TimelineEditKind.SET_TIMES, 20, 40,
                        Map.of("start", at(15).toString(), "end", at(40).toString()))));

        assertThat(result.events().get(0).getEndTime()).isEqualTo(at(15));
        assertThat(result.events().get(1).getStartTime()).isEqualTo(at(15));
        assertThat(result.events().get(1).getEndTime()).isEqualTo(at(40));
    }

    @Test
    void splitsAStayIntoTwoIndependentlyEditableStays() {
        TimelineEditResult result = applier.apply(List.of(stay(0, 40, "A")), List.of(
                edit(1, TimelineEditKind.SPLIT_STAY, 0, 40, Map.of(
                        "splitAt", at(15).toString(),
                        "rightLocationName", "B",
                        "rightLatitude", 2.0,
                        "rightLongitude", 3.0))));

        assertThat(result.events()).hasSize(2);
        assertThat(result.events().get(0).getEndTime()).isEqualTo(at(15));
        assertThat((Stay) result.events().get(1)).satisfies(right -> {
            assertThat(right.getStartTime()).isEqualTo(at(15));
            assertThat(right.getLocationName()).isEqualTo("B");
        });
    }

    @Test
    void choosingAPlaceReplacesThePreviousPlaceIdentity() {
        Stay stay = stay(0, 40, "Office");
        stay.setFavoriteId(5L);

        TimelineEditResult result = applier.apply(List.of(stay), List.of(
                edit(1, TimelineEditKind.SET_PLACE, 0, 40, Map.of(
                        "locationName", "Cafe", "latitude", 1.5, "longitude", 2.5, "geocodingId", 9L))));

        assertThat((Stay) result.events().get(0)).satisfies(changed -> {
            assertThat(changed.getLocationName()).isEqualTo("Cafe");
            assertThat(changed.getGeocodingId()).isEqualTo(9L);
            assertThat(changed.getFavoriteId()).isNull();
        });
    }

    @Test
    void splitStayGivesTheSecondStayOnlyItsOwnPlace() {
        Stay stay = stay(0, 40, "Office");
        stay.setFavoriteId(5L);
        stay.setGeocodingId(7L);

        TimelineEditResult result = applier.apply(List.of(stay), List.of(
                edit(1, TimelineEditKind.SPLIT_STAY, 0, 40, Map.of(
                        "splitAt", at(15).toString(),
                        "rightLocationName", "Gym",
                        "rightLatitude", 2.0,
                        "rightLongitude", 3.0,
                        "rightFavoriteId", 8L))));

        assertThat((Stay) result.events().get(0)).satisfies(left -> {
            assertThat(left.getFavoriteId()).isEqualTo(5L);
            assertThat(left.getGeocodingId()).isEqualTo(7L);
        });
        assertThat((Stay) result.events().get(1)).satisfies(right -> {
            assertThat(right.getLocationName()).isEqualTo("Gym");
            assertThat(right.getFavoriteId()).isEqualTo(8L);
            assertThat(right.getGeocodingId()).isNull();
        });
    }

    @Test
    void mergesSameTypeEventsAcrossFalseMiddleEvent() {
        TimelineEditResult result = applier.apply(List.of(
                stay(0, 15, "A"), trip(15, 5, TripType.WALK), stay(20, 20, "A")), List.of(
                edit(1, TimelineEditKind.MERGE, 0, 15, Map.of("direction", "NEXT"))));

        assertThat(result.events()).singleElement().isInstanceOfSatisfying(Stay.class, merged -> {
            assertThat(merged.getStartTime()).isEqualTo(at(0));
            assertThat(merged.getEndTime()).isEqualTo(at(40));
        });
    }

    @Test
    void doesNotMergeAcrossMultipleEventsOrIncompatiblePlaces() {
        Stay farAway = stay(25, 15, "B");
        farAway.setLatitude(20);
        farAway.setLongitude(20);
        TimelineEditResult tooFar = applier.apply(List.of(
                stay(0, 10, "A"), trip(10, 5, TripType.WALK), trip(15, 10, TripType.CAR), stay(25, 15, "A")), List.of(
                edit(10, TimelineEditKind.MERGE, 0, 10, Map.of("direction", "NEXT"))));
        TimelineEditResult incompatible = applier.apply(List.of(
                stay(0, 15, "A"), trip(15, 10, TripType.WALK), farAway), List.of(
                edit(11, TimelineEditKind.MERGE, 0, 15, Map.of("direction", "NEXT"))));

        assertThat(tooFar.unappliedEditIds()).containsExactly(10L);
        assertThat(incompatible.unappliedEditIds()).containsExactly(11L);
    }

    @Test
    void removalRequiresAndAppliesExplicitResolution() {
        List<TimelineEvent> source = List.of(stay(0, 15, "A"), trip(15, 10, TripType.CAR), stay(25, 15, "B"));

        TimelineEditResult gap = applier.apply(source, List.of(
                edit(1, TimelineEditKind.REMOVE_EVENT, 15, 25, Map.of("resolution", "GAP"))));
        assertThat(gap.events().get(1)).isInstanceOf(DataGap.class);

        TimelineEditResult extended = applier.apply(source, List.of(
                edit(2, TimelineEditKind.REMOVE_EVENT, 15, 25, Map.of("resolution", "EXTEND_PREVIOUS"))));
        assertThat(extended.events()).hasSize(2);
        assertThat(extended.events().get(0).getEndTime()).isEqualTo(at(25));
    }

    @Test
    void convertsGapToTripWithExplicitEndpoints() {
        TimelineEditResult result = applier.apply(List.of(DataGap.fromTimeRange(at(10), at(30))), List.of(
                edit(1, TimelineEditKind.FORCE_TRIP, 10, 30, Map.of(
                        "movementType", "FLIGHT",
                        "startLatitude", 50.0,
                        "startLongitude", 30.0,
                        "endLatitude", 51.0,
                        "endLongitude", 31.0))));

        assertThat(result.events()).singleElement().isInstanceOfSatisfying(Trip.class, converted -> {
            assertThat(converted.getTripType()).isEqualTo(TripType.FLIGHT);
            assertThat(converted.getStartPoint().getLatitude()).isEqualTo(50.0);
            assertThat(converted.getEndPoint().getLongitude()).isEqualTo(31.0);
        });
    }

    @Test
    void leavesUnmatchedEditUnapplied() {
        TimelineEditResult result = applier.apply(List.of(stay(0, 10, "A")), List.of(
                edit(7, TimelineEditKind.SET_VISIT_NAME, 30, 40, Map.of("visitName", "Gone"))));

        assertThat(result.appliedEditIds()).isEmpty();
        assertThat(result.unappliedEditIds()).containsExactly(7L);
    }

    @Test
    void matchesTripAfterSmallTimingAndEndpointDrift() {
        TimelineEditResult result = applier.apply(List.of(trip(1, 60, TripType.CAR)), List.of(
                edit(8, TimelineEditKind.SET_MOVEMENT_TYPE, 0, 60, Map.of(
                        "movementType", "TRAIN",
                        "sourceStartLatitude", 1.001,
                        "sourceStartLongitude", 1.001,
                        "sourceEndLatitude", 2.001,
                        "sourceEndLongitude", 2.001))));

        assertThat(result.appliedEditIds()).containsExactly(8L);
        assertThat(((Trip) result.events().getFirst()).getTripType()).isEqualTo(TripType.TRAIN);
    }

    @Test
    void rejectsTemporallySimilarTripWithUnrelatedEndpoints() {
        TimelineEditResult result = applier.apply(List.of(trip(0, 60, TripType.CAR)), List.of(
                edit(9, TimelineEditKind.SET_MOVEMENT_TYPE, 0, 60, Map.of(
                        "movementType", "TRAIN",
                        "sourceStartLatitude", 40.0,
                        "sourceStartLongitude", 40.0,
                        "sourceEndLatitude", 41.0,
                        "sourceEndLongitude", 41.0))));

        assertThat(result.appliedEditIds()).isEmpty();
        assertThat(result.unappliedEditIds()).containsExactly(9L);
        assertThat(((Trip) result.events().getFirst()).getTripType()).isEqualTo(TripType.CAR);
    }

    private static TimelineEditCommand edit(long id, TimelineEditKind kind, long fromMinute, long toMinute,
                                            Map<String, Object> payload) {
        return new TimelineEditCommand(id, id, kind, at(fromMinute), at(toMinute), payload);
    }

    private static Stay stay(long fromMinute, long durationMinutes, String name) {
        return Stay.builder()
                .startTime(at(fromMinute))
                .duration(Duration.ofMinutes(durationMinutes))
                .locationName(name)
                .latitude(1)
                .longitude(1)
                .build();
    }

    private static Trip trip(long fromMinute, long durationMinutes, TripType type) {
        return Trip.builder()
                .startTime(at(fromMinute))
                .duration(Duration.ofMinutes(durationMinutes))
                .tripType(type)
                .startPoint(GPSPoint.builder().timestamp(at(fromMinute)).latitude(1).longitude(1).build())
                .endPoint(GPSPoint.builder().timestamp(at(fromMinute + durationMinutes)).latitude(2).longitude(2).build())
                .build();
    }

    private static Instant at(long minutes) {
        return ZERO.plus(Duration.ofMinutes(minutes));
    }
}
