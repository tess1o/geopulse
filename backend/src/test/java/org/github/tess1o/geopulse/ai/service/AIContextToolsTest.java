package org.github.tess1o.geopulse.ai.service;

import jakarta.ws.rs.NotFoundException;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.geofencing.service.GeofenceEventService;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoDto;
import org.github.tess1o.geopulse.immich.model.ImmichPreferences;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoSearchRequest;
import org.github.tess1o.geopulse.immich.model.ImmichPhotoSearchResponse;
import org.github.tess1o.geopulse.immich.service.ImmichService;
import org.github.tess1o.geopulse.notes.service.TimelineNoteService;
import org.github.tess1o.geopulse.shared.api.ApiErrorCode;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.streaming.service.explanation.TimelineEventExplanationService;
import org.github.tess1o.geopulse.timelinelabels.service.TimelineLabelService;
import org.github.tess1o.geopulse.trips.service.TripPlanItemService;
import org.github.tess1o.geopulse.trips.service.TripService;
import org.github.tess1o.geopulse.trips.service.TripSummaryService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.weather.dto.WeatherSamplesResponse;
import org.github.tess1o.geopulse.weather.service.WeatherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class AIContextToolsTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private TripService tripService;
    private ImmichService immichService;
    private WeatherService weatherService;
    private TimelineEventExplanationService explanationService;
    private AIContextTools tools;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        CurrentUserService currentUserService = Mockito.mock(CurrentUserService.class);
        tripService = Mockito.mock(TripService.class);
        immichService = Mockito.mock(ImmichService.class);
        weatherService = Mockito.mock(WeatherService.class);
        explanationService = Mockito.mock(TimelineEventExplanationService.class);
        tools = new AIContextTools(currentUserService, tripService, Mockito.mock(TripSummaryService.class),
                Mockito.mock(TripPlanItemService.class), Mockito.mock(TimelineLabelService.class),
                Mockito.mock(TimelineNoteService.class), immichService, weatherService,
                Mockito.mock(GeofenceEventService.class), explanationService, Mockito.mock(GpsPointService.class));

        user = new UserEntity();
        user.setId(USER_ID);
        user.setTimezone("UTC");
        user.setImmichPreferences(ImmichPreferences.builder().enabled(true).serverUrl("http://immich").apiKey("key").build());
        when(currentUserService.getCurrentUserId()).thenReturn(USER_ID);
        when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void listTrips_rejectsUnknownStatus() {
        AIToolException error = assertThrows(AIToolException.class, () -> tools.listTrips("someday"));

        assertEquals("INVALID_ARGUMENT", error.getCode());
    }

    @Test
    void getTripDetails_missingTripIsToolError() {
        when(tripService.getTrip(USER_ID, 5L)).thenThrow(new NotFoundException("missing"));

        AIToolException error = assertThrows(AIToolException.class, () -> tools.getTripDetails(5L));

        assertEquals("TRIP_NOT_FOUND", error.getCode());
    }

    @Test
    void searchPhotos_stripsImageUrlsAndCoversWholeDays() {
        when(immichService.searchPhotos(eq(USER_ID), any())).thenReturn(CompletableFuture.completedFuture(
                ImmichPhotoSearchResponse.builder()
                        .photos(List.of(ImmichPhotoDto.builder().id("p1").thumbnailUrl("/thumb")
                                .previewUrl("/preview").downloadUrl("/download").build()))
                        .totalCount(1)
                        .build()));

        ImmichPhotoSearchResponse response = tools.searchPhotos("2026-03-01", "2026-03-02",
                null, null, null, null, null, null);

        ImmichPhotoDto photo = response.getPhotos().get(0);
        assertEquals("p1", photo.getId());
        assertNull(photo.getThumbnailUrl());
        assertNull(photo.getDownloadUrl());
        ArgumentCaptor<ImmichPhotoSearchRequest> request = ArgumentCaptor.forClass(ImmichPhotoSearchRequest.class);
        verify(immichService).searchPhotos(eq(USER_ID), request.capture());
        assertEquals(OffsetDateTime.parse("2026-03-01T00:00Z"), request.getValue().getStartDate());
        assertEquals(OffsetDateTime.parse("2026-03-02T23:59:59.999Z"), request.getValue().getEndDate());
        assertEquals(AIContextTools.DEFAULT_PHOTOS_LIMIT, request.getValue().getLimit());
    }

    @Test
    void searchPhotos_asyncDomainErrorKeepsItsCode() {
        when(immichService.searchPhotos(eq(USER_ID), any())).thenReturn(CompletableFuture.failedFuture(
                new GeoPulseException(ApiErrorCode.INVALID_IMMICH_SEARCH, "Immich is not configured")));

        AIToolException error = assertThrows(AIToolException.class,
                () -> tools.searchPhotos("2026-03-01", "2026-03-02", null, null, null, null, null, null));

        assertEquals("INVALID_IMMICH_SEARCH", error.getCode());
    }

    @Test
    void searchPhotos_withoutImmichIsToolError() {
        user.setImmichPreferences(null);

        AIToolException error = assertThrows(AIToolException.class,
                () -> tools.searchPhotos("2026-03-01", "2026-03-02", null, null, null, null, null, null));

        assertEquals("IMMICH_NOT_CONFIGURED", error.getCode());
    }

    @Test
    void searchPhotos_requiresCompleteSearchCircle() {
        AIToolException error = assertThrows(AIToolException.class,
                () -> tools.searchPhotos("2026-03-01", "2026-03-02", 50.0, null, null, null, null, null));

        assertEquals("INVALID_ARGUMENT", error.getCode());
    }

    @Test
    void getWeather_disabledIntegrationIsToolError() {
        when(weatherService.findSamples(eq(USER_ID), any(), any(), any(), any(), any(), any()))
                .thenReturn(WeatherSamplesResponse.builder().enabled(false).configured(false).build());

        AIToolException error = assertThrows(AIToolException.class,
                () -> tools.getWeather("2026-03-01", "2026-03-02", null, null, null, null));

        assertEquals("WEATHER_NOT_CONFIGURED", error.getCode());
    }

    @Test
    void explainTimelineEvent_unknownEventIsToolError() {
        when(explanationService.explainTrip(9L, USER_ID)).thenReturn(Optional.empty());

        AIToolException error = assertThrows(AIToolException.class, () -> tools.explainTimelineEvent("trip", 9L));

        assertEquals("TIMELINE_EVENT_NOT_FOUND", error.getCode());
    }

    @Test
    void thinEvenly_keepsFirstAndLastAndRequestedCount() {
        List<Integer> items = IntStream.range(0, 101).boxed().toList();

        List<Integer> thinned = AIContextTools.thinEvenly(items, 5);

        assertEquals(List.of(0, 25, 50, 75, 100), thinned);
    }
}
