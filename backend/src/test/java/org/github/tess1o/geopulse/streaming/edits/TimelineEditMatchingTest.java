package org.github.tess1o.geopulse.streaming.edits;

import org.github.tess1o.geopulse.streaming.model.domain.*;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TimelineEditMatchingTest {
    private static final Instant ZERO = Instant.parse("2026-09-15T10:00:00Z");
    private final TimelineEditApplier applier = new TimelineEditApplier();

    static Stream<Arguments> attributesAndShifts() {
        return Stream.of(TimelineEditKind.SET_MOVEMENT_TYPE, TimelineEditKind.SET_PLACE, TimelineEditKind.SET_VISIT_NAME)
                .flatMap(kind -> Stream.of(-60, -40, -30, -29, -20, -10, 10, 20, 29, 30, 40, 60)
                        .map(shift -> Arguments.of(kind, shift, Math.abs(shift) < 30)));
    }

    @ParameterizedTest
    @MethodSource("attributesAndShifts")
    void attributesRequireStrictMutualMajorityOverlap(TimelineEditKind kind, int shift, boolean matches) {
        TimelineEvent source = kind == TimelineEditKind.SET_MOVEMENT_TYPE ? trip(shift, shift + 60) : stay(shift, shift + 60);
        Map<String, Object> payload = switch (kind) {
            case SET_MOVEMENT_TYPE -> Map.of("movementType", "TRAIN");
            case SET_PLACE -> Map.of("locationName", "Corrected", "latitude", 2.0, "longitude", 2.0);
            case SET_VISIT_NAME -> Map.of("visitName", "Corrected");
            default -> throw new IllegalArgumentException();
        };
        var result = applier.apply(List.of(source), List.of(edit(1, kind, payload)));
        assertThat(result.appliedEditIds()).containsExactlyElementsOf(matches ? List.of(1L) : List.of());
        assertThat(result.unappliedEditIds()).containsExactlyElementsOf(matches ? List.of() : List.of(1L));
        assertThat(result.events()).hasSize(1);
        var target = result.events().getFirst();
        assertThat(target.getStartTime()).isEqualTo(at(shift));
        assertThat(target.getEndTime()).isEqualTo(at(shift + 60));
        if (target instanceof Trip actual) assertThat(actual.getTripType()).isEqualTo(matches ? TripType.TRAIN : TripType.CAR);
        if (target instanceof Stay actual) {
            assertThat(actual.getLocationName()).isEqualTo(matches && kind == TimelineEditKind.SET_PLACE ? "Corrected" : "Original");
            assertThat(actual.getVisitName()).isEqualTo(matches && kind == TimelineEditKind.SET_VISIT_NAME ? "Corrected" : null);
        }
    }

    @ParameterizedTest
    @CsvSource({"0,120,false", "15,45,false", "0,119,true", "15,46,true", "-60,60,false", "-59,60,true"})
    void overlapMustCoverBothTheAnchorAndTheCandidate(int start, int end, boolean matches) {
        var result = applier.apply(List.of(stay(start, end)), List.of(edit(1, TimelineEditKind.SET_VISIT_NAME, Map.of("visitName", "Label"))));
        assertThat(result.appliedEditIds()).hasSize(matches ? 1 : 0);
        assertThat(((Stay) result.events().getFirst()).getVisitName()).isEqualTo(matches ? "Label" : null);
    }

    static Stream<Arguments> tripKindsAndDistances() {
        return Stream.of(TimelineEditKind.SET_MOVEMENT_TYPE, TimelineEditKind.SET_TIMES, TimelineEditKind.MERGE, TimelineEditKind.REMOVE_EVENT)
                .flatMap(kind -> Stream.of(new double[]{1999, 0}, new double[]{0, 1999}, new double[]{1999, 1999},
                                new double[]{2001, 0}, new double[]{0, 2001}, new double[]{2001, 2001})
                        .map(distance -> Arguments.of(kind, distance[0], distance[1], distance[0] < 2000 && distance[1] < 2000)));
    }

    @ParameterizedTest
    @MethodSource("tripKindsAndDistances")
    void bothTripEndpointsMustRemainWithinTwoKilometers(TimelineEditKind kind, double startMeters, double endMeters, boolean matches) {
        var payload = new LinkedHashMap<>(payload(kind));
        payload.putAll(Map.of("sourceStartLatitude", 0.0, "sourceStartLongitude", startMeters / 6371000.0 * 180 / Math.PI,
                "sourceEndLatitude", 0.0, "sourceEndLongitude", 1.0 + endMeters / 6371000.0 * 180 / Math.PI));
        var result = applier.apply(List.of(trip(0, 60), trip(60, 120)), List.of(edit(1, kind, payload)));
        assertThat(result.appliedEditIds()).hasSize(matches ? 1 : 0);
        if (kind == TimelineEditKind.SET_MOVEMENT_TYPE) assertThat(((Trip) result.events().getFirst()).getTripType()).isEqualTo(matches ? TripType.TRAIN : TripType.CAR);
        if (!matches) {
            assertThat(result.events()).hasSize(2);
            assertThat(result.events().getFirst().getStartTime()).isEqualTo(at(0));
            assertThat(result.events().getFirst().getEndTime()).isEqualTo(at(60));
        }
    }

    @Test
    void splitAndMergedCandidatesDoNotReceiveAnEditWithoutAMutualMajority() {
        var label = edit(1, TimelineEditKind.SET_VISIT_NAME, Map.of("visitName", "Label"));
        var split = applier.apply(List.of(stay(0, 30), stay(30, 60)), List.of(label));
        assertThat(split.unappliedEditIds()).containsExactly(1L);
        assertThat(split.events()).allSatisfy(event -> assertThat(((Stay) event).getVisitName()).isNull());
        var merged = applier.apply(List.of(stay(0, 120)), List.of(label));
        assertThat(merged.unappliedEditIds()).containsExactly(1L);
        var majority = applier.apply(List.of(stay(0, 20), stay(20, 60)), List.of(label));
        assertThat(((Stay) majority.events().get(0)).getVisitName()).isNull();
        assertThat(((Stay) majority.events().get(1)).getVisitName()).isEqualTo("Label");
    }

    @ParameterizedTest
    @EnumSource(value = TimelineEditKind.class, names = {"SET_MOVEMENT_TYPE", "SET_PLACE", "SET_VISIT_NAME", "SET_TIMES", "SPLIT_STAY", "MERGE", "REMOVE_EVENT"})
    void missingTargetsStayUnapplied(TimelineEditKind kind) {
        var result = applier.apply(List.of(stay(120, 180)), List.of(edit(1, kind, payload(kind))));
        assertThat(result.unappliedEditIds()).containsExactly(1L);
        assertThat(result.events()).singleElement().satisfies(event -> {
            assertThat(event.getStartTime()).isEqualTo(at(120)); assertThat(event.getEndTime()).isEqualTo(at(180));
        });
    }

    @ParameterizedTest
    @CsvSource({"SET_TIMES,-10", "SET_TIMES,10", "SET_TIMES,20", "SPLIT_STAY,-10", "SPLIT_STAY,10", "SPLIT_STAY,20", "MERGE,-10", "MERGE,10", "REMOVE_EVENT,-10", "REMOVE_EVENT,10"})
    void structuralEditsFollowMatchedEventsButKeepTheirExplicitTimes(TimelineEditKind kind, int shift) {
        var result = applier.apply(List.of(stay(-60, shift), stay(shift, shift + 60), stay(shift + 60, 180)),
                List.of(edit(1, kind, payload(kind))));
        assertThat(result.appliedEditIds()).containsExactly(1L);
        if (kind == TimelineEditKind.SET_TIMES) {
            assertThat(result.events().get(1).getStartTime()).isEqualTo(at(5));
            assertThat(result.events().get(1).getEndTime()).isEqualTo(at(55));
        } else if (kind == TimelineEditKind.SPLIT_STAY) {
            assertThat(result.events()).hasSize(4);
            assertThat(result.events().get(1).getEndTime()).isEqualTo(at(30));
            assertThat(result.events().get(2).getStartTime()).isEqualTo(at(30));
        } else if (kind == TimelineEditKind.MERGE) {
            assertThat(result.events()).hasSize(2);
            assertThat(result.events().getLast().getStartTime()).isEqualTo(at(shift));
            assertThat(result.events().getLast().getEndTime()).isEqualTo(at(180));
        } else assertThat(result.events().get(1)).isInstanceOf(DataGap.class);
    }

    @ParameterizedTest
    @EnumSource(value = TimelineEditKind.class, names = {"FORCE_STAY", "FORCE_TRIP"})
    void absoluteCorrectionsKeepTheirIntervalEvenWithoutASourceEvent(TimelineEditKind kind) {
        var result = applier.apply(List.of(stay(10, 70)), List.of(edit(1, kind, payload(kind))));
        assertThat(result.appliedEditIds()).containsExactly(1L);
        assertThat(result.events().getFirst().getStartTime()).isEqualTo(at(0));
        assertThat(result.events().getFirst().getEndTime()).isEqualTo(at(60));
        assertThat(result.events().getLast().getStartTime()).isEqualTo(at(60));
        assertThat(result.events().getLast().getEndTime()).isEqualTo(at(70));
    }

    @Test
    void aSplitThatNoLongerFitsIsSkippedWithoutBlockingLaterEdits() {
        var stale = edit(1, TimelineEditKind.SPLIT_STAY, Map.of("splitAt", at(5).toString()));
        var label = edit(2, TimelineEditKind.SET_VISIT_NAME, Map.of("visitName", "Still works"));
        var result = applier.apply(List.of(stay(10, 70)), List.of(stale, label));
        assertThat(result.unappliedEditIds()).containsExactly(1L);
        assertThat(result.appliedEditIds()).containsExactly(2L);
        assertThat(result.events()).singleElement().satisfies(event -> {
            assertThat(event.getStartTime()).isEqualTo(at(10)); assertThat(event.getEndTime()).isEqualTo(at(70));
            assertThat(((Stay) event).getVisitName()).isEqualTo("Still works");
        });
    }

    @Test
    void staleTimeBoundariesDoNotPartiallyResizeNeighbours() {
        var stale = edit(1, TimelineEditKind.SET_TIMES, Map.of("start", at(10).toString(), "end", at(130).toString()));
        var result = applier.apply(List.of(stay(-60, 0), stay(0, 60), stay(60, 120)), List.of(stale));
        assertThat(result.unappliedEditIds()).containsExactly(1L);
        assertThat(result.events()).extracting(TimelineEvent::getStartTime).containsExactly(at(-60), at(0), at(60));
        assertThat(result.events()).extracting(TimelineEvent::getEndTime).containsExactly(at(0), at(60), at(120));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXTEND_PREVIOUS", "EXTEND_NEXT", "MERGE"})
    void removalWithoutItsRequiredNeighboursDoesNotDeleteAnything(String resolution) {
        var result = applier.apply(List.of(stay(0, 60)), List.of(edit(1, TimelineEditKind.REMOVE_EVENT, Map.of("resolution", resolution))));
        assertThat(result.unappliedEditIds()).containsExactly(1L);
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().getFirst().getStartTime()).isEqualTo(at(0));
        assertThat(result.events().getFirst().getEndTime()).isEqualTo(at(60));
    }

    @ParameterizedTest
    @EnumSource(value = TimelineEditKind.class, names = {"SET_TIMES", "MERGE", "REMOVE_EVENT"})
    void structuralEditsDoNotJumpFromAStayToAReplacementTrip(TimelineEditKind kind) {
        var values = new LinkedHashMap<>(payload(kind)); values.put("sourceType", "STAY");
        var result = applier.apply(List.of(trip(0, 60), trip(60, 120)), List.of(edit(1, kind, values)));
        assertThat(result.unappliedEditIds()).containsExactly(1L);
        assertThat(result.events()).hasSize(2);
        assertThat(result.events().getFirst().getStartTime()).isEqualTo(at(0));
        assertThat(result.events().getFirst().getEndTime()).isEqualTo(at(60));
    }

    private static Map<String, Object> payload(TimelineEditKind kind) {
        return switch (kind) {
            case SET_MOVEMENT_TYPE -> Map.of("movementType", "TRAIN");
            case SET_PLACE, FORCE_STAY -> Map.of("locationName", "Corrected", "latitude", 0.0, "longitude", 0.0);
            case SET_VISIT_NAME -> Map.of("visitName", "Label");
            case SET_TIMES -> Map.of("start", at(5).toString(), "end", at(55).toString());
            case SPLIT_STAY -> Map.of("splitAt", at(30).toString());
            case MERGE -> Map.of("direction", "NEXT");
            case REMOVE_EVENT -> Map.of("resolution", "GAP");
            case FORCE_TRIP -> Map.of("movementType", "TRAIN", "startLatitude", 0.0, "startLongitude", 0.0, "endLatitude", 0.0, "endLongitude", 1.0);
        };
    }
    private static TimelineEditCommand edit(long id, TimelineEditKind kind, Map<String, Object> payload) { return new TimelineEditCommand(id, id, kind, at(0), at(60), payload); }
    private static Stay stay(int start, int end) { return Stay.builder().startTime(at(start)).duration(Duration.ofMinutes(end - start)).latitude(0).longitude(0).locationName("Original").build(); }
    private static Trip trip(int start, int end) { return Trip.builder().startTime(at(start)).duration(Duration.ofMinutes(end - start)).tripType(TripType.CAR)
            .startPoint(GPSPoint.builder().timestamp(at(start)).latitude(0).longitude(0).build())
            .endPoint(GPSPoint.builder().timestamp(at(end)).latitude(0).longitude(1).build()).build(); }
    private static Instant at(int minutes) { return ZERO.plusSeconds(minutes * 60L); }
}
