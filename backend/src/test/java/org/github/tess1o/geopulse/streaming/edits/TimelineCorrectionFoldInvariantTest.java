package org.github.tess1o.geopulse.streaming.edits;

import org.github.tess1o.geopulse.streaming.edits.payload.*;
import org.github.tess1o.geopulse.streaming.model.domain.*;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Exercises the real fold with reproducible generated intervals, including boundary instants. */
@Tag("unit")
class TimelineCorrectionFoldInvariantTest {
    private static final Instant START=Instant.parse("2026-09-15T08:00:00Z");
    private final TimelineEditApplier fold=new TimelineEditApplier();

    @Test void nestedFillsKeepCoverageAndProvenanceAndDoNotMutateTheirInputs() {
        for (int seed=0;seed<100;seed++) {
            var random=new Random(seed);
            int from=1+random.nextInt(1200), to=2400+random.nextInt(1199);
            var source=Stay.builder().startTime(START).duration(Duration.ofHours(1)).locationName("Detected").build();
            var outer=command(1,TimelineEditKind.FORCE_STAY,0,3600,PlaceStayPayload.builder().latitude(49.8).longitude(24.1).locationName("Outer").build());
            var inner=command(2,TimelineEditKind.FORCE_STAY,from,to,PlaceStayPayload.builder().latitude(49.9).longitude(24.2).locationName("Inner").build());
            var result=fold.apply(List.of(source),List.of(outer,inner));
            assertThat(result.events()).hasSize(3);
            assertThat(result.effects().get(1L)).hasSize(2);
            assertThat(result.effects().get(2L)).hasSize(1);
            assertThat(result.unappliedEditIds()).isEmpty();
            assertThat(result.events().stream().mapToLong(e->e.getDuration().toSeconds()).sum()).isEqualTo(3600);
            for (int i=1;i<result.events().size();i++) assertThat(result.events().get(i).getStartTime()).isEqualTo(result.events().get(i-1).getEndTime());
            assertThat(source.getLocationName()).isEqualTo("Detected"); assertThat(source.getDuration()).isEqualTo(Duration.ofHours(1));
            var repeated=fold.apply(List.of(source),List.of(outer,inner));
            assertThat(repeated.events()).isEqualTo(result.events());
            assertThat(fold.apply(List.of(source),List.of(inner)).events()).anySatisfy(e->assertThat(((Stay)e).getLocationName()).isEqualTo("Inner"));
        }
    }

    @Test void clearingAWholeFillMakesItDormantAndRemovingTheClearRestoresIt() {
        var fill=command(1,TimelineEditKind.FORCE_STAY,0,3600,PlaceStayPayload.builder().latitude(49.8).longitude(24.1).locationName("Visit").build());
        var clear=command(2,TimelineEditKind.REMOVE_EVENT,0,3600,new ClearPayload(ClearPayload.RemovalResolution.GAP));
        var result=fold.apply(List.of(),List.of(fill,clear));
        assertThat(result.appliedEditIds()).containsExactly(2L);
        assertThat(result.unappliedEditIds()).containsExactly(1L);
        assertThat(result.events()).singleElement().isInstanceOf(DataGap.class);
        assertThat(fold.apply(List.of(),List.of(fill)).events()).singleElement().isInstanceOf(Stay.class);
    }

    @Test void fillsCarveAZeroDurationConnectionAtTheirInclusiveStartButNotTheirExclusiveEnd() {
        var atStart=Trip.builder().startTime(START).duration(Duration.ZERO).build();
        var atEnd=Trip.builder().startTime(START.plusSeconds(60)).duration(Duration.ZERO).build();
        var fill=command(1,TimelineEditKind.FORCE_STAY,0,60,PlaceStayPayload.builder().latitude(49.8).longitude(24.1).locationName("Visit").build());
        var result=fold.apply(List.of(atStart,atEnd),List.of(fill));
        assertThat(result.events()).hasSize(2);
        assertThat(result.events()).filteredOn(Trip.class::isInstance).singleElement().satisfies(e->assertThat(e.getStartTime()).isEqualTo(START.plusSeconds(60)));
    }

    @Test void clippingManualRoutesPreservesDistanceIncludingDuplicatePointsAndTheDateline() {
        for (var route : List.of(
                List.of(point(49.8,24),point(49.8,24),point(49.8,24.12)),
                List.of(point(0,179),point(0,-179)),
                List.of(point(49.8,24),point(49.8,24)))) {
            double distance=ManualRouteGeometry.distance(route);
            var left=ManualRouteGeometry.slice(route,0,.25); var middle=ManualRouteGeometry.slice(route,.25,.75); var right=ManualRouteGeometry.slice(route,.75,1);
            assertThat(ManualRouteGeometry.distance(left)+ManualRouteGeometry.distance(middle)+ManualRouteGeometry.distance(right)).isCloseTo(distance,within(0.05));
            assertThat(left.getLast()).isEqualTo(middle.getFirst()); assertThat(middle.getLast()).isEqualTo(right.getFirst());
            assertThat(left).hasSizeGreaterThanOrEqualTo(2); assertThat(right).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Test void emptyLabelsAndRouteChangesApplyAndUndoByReplay() {
        var source=Stay.builder().startTime(START).duration(Duration.ofHours(1)).locationName("Place").visitName("Label").build();
        var clear=command(1,TimelineEditKind.SET_VISIT_NAME,0,3600,new SetLabelPayload(""));
        assertThat(fold.apply(List.of(source),List.of(clear)).events()).singleElement().satisfies(e->assertThat(((Stay)e).getVisitName()).isNull());
        assertThat(source.getVisitName()).isEqualTo("Label");
        assertThat(fold.apply(List.of(source),List.of()).events()).singleElement().satisfies(e->assertThat(((Stay)e).getVisitName()).isEqualTo("Label"));
    }

    @Test void resizingAManualTripChangesItsTimesWithoutChangingItsRoute() {
        var route = List.of(point(49.8,24), point(49.9,24.2), point(49.8,24.12));
        var source = Trip.builder().startTime(START).duration(Duration.ofHours(1)).manualRoute(route)
                .distanceMeters(ManualRouteGeometry.distance(route)).build();
        var resized = command(1, TimelineEditKind.SET_TIMES, 0, 3600, new ResizePayload(START.plusSeconds(600), START.plusSeconds(3000)));
        var result = fold.apply(List.of(source), List.of(resized));
        assertThat(result.events()).singleElement().isInstanceOfSatisfying(Trip.class, trip -> {
            assertThat(trip.getDuration()).isEqualTo(Duration.ofMinutes(40));
            assertThat(trip.getManualRoute()).containsExactlyElementsOf(route);
            assertThat(trip.getDistanceMeters()).isEqualTo(source.getDistanceMeters());
            assertThat(trip.getStartPoint().getTimestamp()).isEqualTo(START.plusSeconds(600));
        });
    }

    private static TripReconstructionWaypointDto point(double lat,double lon) { return new TripReconstructionWaypointDto(lat,lon); }
    private static TimelineEditCommand command(long id,TimelineEditKind kind,int start,int end,CorrectionPayload payload) { return new TimelineEditCommand(id,id,kind,START.plusSeconds(start),START.plusSeconds(end),payload); }
}
