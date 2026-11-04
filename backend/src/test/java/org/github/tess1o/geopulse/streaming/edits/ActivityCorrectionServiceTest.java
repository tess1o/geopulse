package org.github.tess1o.geopulse.streaming.edits;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditPreviewDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.repository.TimelineEditRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ActivityCorrectionServiceTest {
    private static final Instant START = Instant.parse("2026-10-01T10:00:00Z");
    private final UUID user = UUID.randomUUID();
    private final ActivityCorrectionService service = new ActivityCorrectionService();
    private final TimelineEditService edits = mock(TimelineEditService.class);

    ActivityCorrectionServiceTest() {
        service.edits = edits;
        service.stays = mock(TimelineStayRepository.class);
        service.tripRepository = mock(TimelineTripRepository.class);
        service.editRepository = mock(TimelineEditRepository.class);
        when(edits.preview(eq(user), any())).thenReturn(new TimelineEditPreviewDTO(List.of()));
    }

    @Test
    void sortsActivitiesWithoutChangingInputAndPreservesStayDetails() {
        var early = activity("STAY", 0, 60);
        early.setLatitude(49.0);
        early.setLongitude(24.0);
        early.setFavoriteId(42L);
        early.setLocationName("Home");
        early.setVisitName("  Lunch  ");
        var late = activity("STAY", 60, 120);
        var input = new ArrayList<>(List.of(late, early));

        service.preview(user, new ActivityCorrectionService.Request(null, input, null, null));

        var commands = commands(2);
        assertThat(input).containsExactly(late, early);
        assertThat(commands).extracting(TimelineEditRequest::getAnchorStart).containsExactly(START, START.plusSeconds(60));
        var command = commands.getFirst();
        assertThat(command.getKind()).isEqualTo(TimelineEditKind.FORCE_STAY);
        assertThat(command.getAffectedFrom()).isEqualTo(START);
        assertThat(command.getAffectedTo()).isEqualTo(START.plusSeconds(60));
        assertThat(command.getLatitude()).isEqualTo(49.0);
        assertThat(command.getLongitude()).isEqualTo(24.0);
        assertThat(command.getFavoriteId()).isEqualTo(42L);
        assertThat(command.getPlaceName()).isEqualTo("Home");
        assertThat(command.getPayload()).containsEntry("visitName", "Lunch").containsEntry("activityCorrection", true);
    }

    @Test
    void buildsManualRouteFromResolvedEndpointsAndOptionalWaypoints() {
        resolveEndpoints();
        var trip = trip();
        var middle = new TripReconstructionWaypointDto(49.5, 24.5);
        trip.setWaypoints(List.of(middle));
        trip.setMovementType("WALK");
        service.preview(user, request(trip));
        var command = commands(1).getFirst();
        assertThat(command.getKind()).isEqualTo(TimelineEditKind.FORCE_TRIP);
        assertThat(command.getPayload()).containsEntry("startFavoriteId", 42L)
                .containsEntry("startLatitude", 49.0).containsEntry("endLongitude", 25.0)
                .containsEntry("movementType", "WALK").containsEntry("activityCorrection", true);
        var route = List.of(new TripReconstructionWaypointDto(49.0, 24.0), middle,
                new TripReconstructionWaypointDto(50.0, 25.0));
        assertThat(command.getPayload().get("waypoints")).isEqualTo(route);
        assertThat(command.getPayload().get("distanceMeters")).isEqualTo(Math.round(ManualRouteGeometry.distance(route)));
        assertThat(trip.getWaypoints()).containsExactly(middle);
    }

    @Test
    void acceptsRoutesWithoutIntermediatePointsAndDefaultsMovementType() {
        resolveEndpoints();
        service.preview(user, request(trip()));
        assertThat(commands(1).getFirst().getPayload()).containsEntry("movementType", "UNKNOWN")
                .containsEntry("waypoints", List.of(new TripReconstructionWaypointDto(49.0, 24.0),
                        new TripReconstructionWaypointDto(50.0, 25.0)));
    }

    @Test
    void preservesTheCauseOfInvalidManualGeometry() {
        resolveEndpoints();
        var trip = trip();
        trip.setWaypoints(List.of(new TripReconstructionWaypointDto(Double.NaN, 24.0)));
        assertThatThrownBy(() -> service.preview(user, request(trip)))
                .isInstanceOf(GeoPulseException.class).hasCauseInstanceOf(IllegalArgumentException.class);
        verify(edits, never()).preview(any(), any());
    }

    @Test
    void rejectsInvalidIntervalsOverlapsAndCountsBeforePreviewingAnything() {
        for (var request : Arrays.asList(null, request(), request((ActivityCorrectionService.Activity) null),
                request(activity("STAY", 0, 0)), request(activity("STAY", 60, 0)),
                request(activity("STAY", 0, 120), activity("STAY", 60, 180)),
                new ActivityCorrectionService.Request(null, Collections.nCopies(101, activity("STAY", 0, 60)), null, null))) {
            assertThatThrownBy(() -> service.preview(user, request)).isInstanceOf(GeoPulseException.class);
        }
        var future = activity("STAY", 0, 60);
        future.setEndTime(Instant.now().plusSeconds(3600));
        assertThatThrownBy(() -> service.preview(user, request(future))).isInstanceOf(GeoPulseException.class);
        verify(edits, never()).preview(any(), any());
    }

    @Test
    void rejectsUnknownActivityTypesAndMissingTripEndpoints() {
        assertThatThrownBy(() -> service.preview(user, request(activity("OTHER", 0, 60))))
                .isInstanceOf(GeoPulseException.class).hasMessageContaining("Choose stayed at a place or travelled");
        assertThatThrownBy(() -> service.preview(user, request(activity("TRIP", 0, 60))))
                .isInstanceOf(GeoPulseException.class).hasMessageContaining("Choose both trip endpoints");
    }

    private void resolveEndpoints() {
        when(edits.resolvedPayload(eq(user), any())).thenAnswer(invocation -> {
            TimelineEditRequest command = invocation.getArgument(1);
            assertThat(command.getPayload()).doesNotContainKey("activityCorrection");
            return new LinkedHashMap<>(Map.of("startLatitude", 49.0, "startLongitude", 24.0,
                    "endLatitude", 50.0, "endLongitude", 25.0));
        });
    }

    private List<TimelineEditRequest> commands(int count) {
        var captor = ArgumentCaptor.forClass(TimelineEditRequest.class);
        verify(edits, times(count)).preview(eq(user), captor.capture());
        return captor.getAllValues();
    }

    private ActivityCorrectionService.Activity trip() {
        var trip = activity("TRIP", 0, 60);
        trip.setStartPlace(new ActivityCorrectionService.Place(42L, null, null, null, "Home"));
        trip.setEndPlace(new ActivityCorrectionService.Place(null, null, 50.0, 25.0, "End"));
        return trip;
    }

    private ActivityCorrectionService.Request request(ActivityCorrectionService.Activity... activities) {
        return new ActivityCorrectionService.Request(null, Arrays.asList(activities), null, null);
    }

    private ActivityCorrectionService.Activity activity(String type, long from, long to) {
        var activity = new ActivityCorrectionService.Activity();
        activity.setSegmentType(type);
        activity.setStartTime(START.plusSeconds(from));
        activity.setEndTime(START.plusSeconds(to));
        return activity;
    }
}
