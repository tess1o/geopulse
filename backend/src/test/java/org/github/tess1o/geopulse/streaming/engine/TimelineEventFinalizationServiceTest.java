package org.github.tess1o.geopulse.streaming.engine;

import org.github.tess1o.geopulse.favorites.model.FavoriteAreaDto;
import org.github.tess1o.geopulse.shared.service.LocationPointResolver;
import org.github.tess1o.geopulse.shared.service.LocationResolutionResult;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.UserState;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReason;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReasons;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
/**
 * Unit tests for TimelineEventFinalizationService.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class TimelineEventFinalizationServiceTest {
    @Mock
    private LocationPointResolver locationPointResolver;

    @InjectMocks
    private TimelineEventFinalizationService service;
    private TimelineConfig config;
    @BeforeEach
    void setUp() {
        config = TimelineConfig.builder()
            .staypointMaxAccuracyThreshold(50.0) // 50m threshold
            .useVelocityAccuracy(true)
            .build();
    }
    @Test
    void shouldFinalizeStayAtCentroid() {
        UserState userState = new UserState();
        userState.addActivePoint(createGpsPoint("2024-08-15T08:00:00Z", 40.7589, -73.9851, 30.0));
        userState.addActivePoint(createGpsPoint("2024-08-15T08:05:00Z", 40.7590, -73.9850, 20.0));
        userState.addActivePoint(createGpsPoint("2024-08-15T08:10:00Z", 40.7588, -73.9852, 40.0));
        userState.addActivePoint(createGpsPoint("2024-08-15T08:15:00Z", 40.7587, -73.9853, 80.0));
        Stay result = service.finalizeStayWithoutLocation(userState, config);
        assertNotNull(result);
        assertEquals(Instant.parse("2024-08-15T08:00:00Z"), result.getStartTime());
        // Should be approximately the centroid
        assertEquals(40.7588, result.getLatitude(), 0.001);
        assertEquals(-73.9851, result.getLongitude(), 0.001);
    }
    @Test
    void shouldFinalizeStay_WhenMostPointsAreInaccurate() {
        // Inaccurate points are filtered out before the state machine; the finalizer does not judge accuracy again.
        UserState userState = new UserState();
        userState.addActivePoint(createGpsPoint("2024-08-15T08:00:00Z", 40.7589, -73.9851, 30.0));
        userState.addActivePoint(createGpsPoint("2024-08-15T08:05:00Z", 40.7590, -73.9850, 100.0));
        userState.addActivePoint(createGpsPoint("2024-08-15T08:10:00Z", 40.7588, -73.9852, 80.0));
        userState.addActivePoint(createGpsPoint("2024-08-15T08:15:00Z", 40.7587, -73.9853, 90.0));
        Stay result = service.finalizeStayWithoutLocation(userState, config);
        assertNotNull(result);
    }
    @Test
    void shouldReturnNull_WhenUserStateEmpty() {
        UserState emptyState = new UserState();
        Stay result = service.finalizeStayWithoutLocation(emptyState, config);
        assertNull(result, "Should return null when user state has no active points");
    }

    @Test
    void shouldAnchorStayCoordinates_WhenFavoriteResolutionProvidesAnchorCoordinates() {
        Stay stay = Stay.builder()
                .startTime(Instant.parse("2024-08-15T08:00:00Z"))
                .duration(java.time.Duration.ofMinutes(30))
                .latitude(40.7588)
                .longitude(-73.9851)
                .build();

        List<TimelineEvent> events = List.of(stay);
        UUID userId = UUID.randomUUID();
        Map<String, LocationResolutionResult> results = Map.of(
                "-73.9851,40.7588",
                LocationResolutionResult.fromFavorite("Home", 123L, 40.7580, -73.9855)
        );

        when(locationPointResolver.resolveLocationsWithReferencesBatch(eq(userId), anyList(), isNull()))
                .thenReturn(results);

        service.populateStayLocations(events, userId);

        assertEquals("Home", stay.getLocationName());
        assertEquals(123L, stay.getFavoriteId());
        assertEquals(40.7580, stay.getLatitude(), 0.000001);
        assertEquals(-73.9855, stay.getLongitude(), 0.000001);
    }

    @Test
    void shouldNoteFavoriteArea_WhenStayKeepsPointsOutsideRadiusInItsArea() {
        UserState userState = stayWithPointOutsideRadius();
        userState.setFavoriteAreas(List.of(FavoriteAreaDto.builder().id(7L)
            .southWestLat(40.7570).southWestLon(-73.9870).northEastLat(40.7610).northEastLon(-73.9830).build()));

        service.finalizeStayWithoutLocation(userState, radiusConfig());

        assertTrue(userState.getActiveExplanation().getNotes().stream()
            .anyMatch(note -> TimelineReasons.FAVORITE_AREA.equals(note.code())));
    }

    @Test
    void shouldNotNoteFavoriteArea_WhenStayHasNoFavoriteArea() {
        UserState userState = stayWithPointOutsideRadius();

        service.finalizeStayWithoutLocation(userState, radiusConfig());

        assertTrue(userState.getActiveExplanation().getNotes().isEmpty());
    }

    /** Three points together and one about 90 m from the stay centre, beyond a 50 m radius. */
    private UserState stayWithPointOutsideRadius() {
        UserState userState = new UserState();
        userState.startExplanation(TimelineReason.of(TimelineReasons.FIRST_POINT));
        userState.addActivePoint(createGpsPoint("2024-08-15T08:00:00Z", 40.7589, -73.9851, 10.0));
        userState.addActivePoint(createGpsPoint("2024-08-15T08:05:00Z", 40.7589, -73.9851, 10.0));
        userState.addActivePoint(createGpsPoint("2024-08-15T08:10:00Z", 40.7589, -73.9851, 10.0));
        userState.addActivePoint(createGpsPoint("2024-08-15T08:15:00Z", 40.7600, -73.9851, 10.0));
        return userState;
    }

    private TimelineConfig radiusConfig() {
        return TimelineConfig.builder().staypointRadiusMeters(50).build();
    }

    private GPSPoint createGpsPoint(String timestamp, double lat, double lon, double accuracy) {
        return GPSPoint.builder()
            .timestamp(Instant.parse(timestamp))
            .latitude(lat)
            .longitude(lon)
            .speed(1.0)
            .accuracy(accuracy)
            .build();
    }
}
