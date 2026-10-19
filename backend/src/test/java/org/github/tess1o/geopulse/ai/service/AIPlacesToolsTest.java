package org.github.tess1o.geopulse.ai.service;

import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.streaming.model.dto.CitySummaryDTO;
import org.github.tess1o.geopulse.streaming.service.CurrentActivityService;
import org.github.tess1o.geopulse.streaming.service.LocationAnalyticsService;
import org.github.tess1o.geopulse.streaming.service.PlaceDetailsService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class AIPlacesToolsTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private LocationAnalyticsService locationAnalyticsService;
    private PlaceDetailsService placeDetailsService;
    private AIPlacesTools tools;

    @BeforeEach
    void setUp() {
        CurrentUserService currentUserService = Mockito.mock(CurrentUserService.class);
        locationAnalyticsService = Mockito.mock(LocationAnalyticsService.class);
        placeDetailsService = Mockito.mock(PlaceDetailsService.class);
        tools = new AIPlacesTools(currentUserService, Mockito.mock(CurrentActivityService.class),
                locationAnalyticsService, placeDetailsService, Mockito.mock(FavoriteLocationService.class));

        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setTimezone("Europe/Kyiv");
        when(currentUserService.getCurrentUserId()).thenReturn(USER_ID);
        when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void searchVisitedPlaces_rejectsShortQuery() {
        AIToolException error = assertThrows(AIToolException.class, () -> tools.searchVisitedPlaces("L", null));

        assertEquals("INVALID_ARGUMENT", error.getCode());
        verify(locationAnalyticsService, never()).search(any(), anyString(), any());
    }

    @Test
    void searchVisitedPlaces_passesLowerCaseTypeFilter() {
        tools.searchVisitedPlaces(" Lisbon ", "City");

        verify(locationAnalyticsService).search(USER_ID, "Lisbon", "city");
    }

    @Test
    void searchVisitedPlaces_rejectsUnknownType() {
        AIToolException error = assertThrows(AIToolException.class, () -> tools.searchVisitedPlaces("Lisbon", "street"));

        assertEquals("INVALID_ARGUMENT", error.getCode());
    }

    @Test
    void getPlaceDetails_notFoundIsToolError() {
        when(placeDetailsService.getPlaceDetails("favorite", 7L, USER_ID, "Europe/Kyiv")).thenReturn(Optional.empty());

        AIToolException error = assertThrows(AIToolException.class, () -> tools.getPlaceDetails("FAVORITE", 7L));

        assertEquals("PLACE_NOT_FOUND", error.getCode());
    }

    @Test
    void getPlaceVisits_appliesDefaultsAndCapsPageSize() {
        tools.getPlaceVisits("geocoding", 3L, null, 1000, "stayDuration", "asc");

        verify(placeDetailsService).getPlaceVisits("geocoding", 3L, USER_ID, 0,
                AIPlacesTools.MAX_VISITS_PAGE_SIZE, "stayDuration", "asc");
    }

    @Test
    void listVisitedCities_sortsByTimeSpentAndReportsTruncation() {
        when(locationAnalyticsService.getAllCities(USER_ID)).thenReturn(List.of(
                city("Short", 10), city("Long", 1000), city("Middle", 100)));

        var result = tools.listVisitedCities(2);

        assertEquals(List.of("Long", "Middle"), result.items().stream().map(CitySummaryDTO::getCityName).toList());
        assertEquals(3, result.totalCount());
        assertTrue(result.truncated());
    }

    @Test
    void getCityDetails_unknownCityIsToolError() {
        when(locationAnalyticsService.getCityDetails(USER_ID, "Atlantis")).thenReturn(Optional.empty());

        AIToolException error = assertThrows(AIToolException.class, () -> tools.getCityDetails("Atlantis"));

        assertEquals("CITY_NOT_FOUND", error.getCode());
    }

    private static CitySummaryDTO city(String name, long totalDuration) {
        CitySummaryDTO city = new CitySummaryDTO();
        city.setCityName(name);
        city.setTotalDuration(totalDuration);
        return city;
    }
}
