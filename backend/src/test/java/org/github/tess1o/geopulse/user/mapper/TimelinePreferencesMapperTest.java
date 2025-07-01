package org.github.tess1o.geopulse.user.mapper;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;
import org.github.tess1o.geopulse.user.model.UpdateTimelinePreferencesRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TimelinePreferencesMapperTest {

    @Inject
    TimelinePreferencesMapper mapper;

    @Test
    void testPreferencesToConfig() {
        // Given
        TimelinePreferences preferences = new TimelinePreferences();
        preferences.setStaypointDetectionAlgorithm("enhanced");
        preferences.setUseVelocityAccuracy(true);
        preferences.setStaypointVelocityThreshold(12.0);
        preferences.setTripMinDistanceMeters(100);
        preferences.setIsMergeEnabled(false);

        // When
        TimelineConfig config = mapper.preferencesToConfig(preferences);

        // Then
        assertNotNull(config);
        assertEquals("enhanced", config.getStaypointDetectionAlgorithm());
        assertEquals(true, config.getUseVelocityAccuracy());
        assertEquals(12.0, config.getStaypointVelocityThreshold());
        assertEquals(100, config.getTripMinDistanceMeters());
        assertEquals(false, config.getIsMergeEnabled());
    }

    @Test
    void testRequestToConfig() {
        // Given
        UpdateTimelinePreferencesRequest request = new UpdateTimelinePreferencesRequest();
        request.setStaypointDetectionAlgorithm("claude");
        request.setStaypointVelocityThreshold(15.0);
        request.setTripMinDistanceMeters(200);
        request.setIsMergeEnabled(true);

        // When
        TimelineConfig config = mapper.requestToConfig(request);

        // Then
        assertNotNull(config);
        assertEquals("claude", config.getStaypointDetectionAlgorithm());
        assertEquals(15.0, config.getStaypointVelocityThreshold());
        assertEquals(200, config.getTripMinDistanceMeters());
        assertEquals(true, config.getIsMergeEnabled());
    }

    @Test
    void testUpdatePreferencesFromConfig() {
        // Given
        TimelinePreferences preferences = new TimelinePreferences();
        preferences.setStaypointDetectionAlgorithm("original");
        preferences.setTripMinDistanceMeters(50);

        TimelineConfig config = new TimelineConfig();
        config.setStaypointDetectionAlgorithm("enhanced");
        config.setUseVelocityAccuracy(true);
        config.setStaypointVelocityThreshold(12.0);
        config.setTripMinDistanceMeters(100);
        config.setIsMergeEnabled(false);

        // When
        mapper.updatePreferencesFromConfig(config, preferences);

        // Then
        assertEquals("enhanced", preferences.getStaypointDetectionAlgorithm());
        assertEquals(true, preferences.getUseVelocityAccuracy());
        assertEquals(12.0, preferences.getStaypointVelocityThreshold());
        assertEquals(100, preferences.getTripMinDistanceMeters());
        assertEquals(false, preferences.getIsMergeEnabled());
    }

    @Test
    void testConfigToPreferences() {
        // Given
        TimelineConfig config = new TimelineConfig();
        config.setStaypointDetectionAlgorithm("claude");
        config.setUseVelocityAccuracy(false);
        config.setStaypointVelocityThreshold(20.0);
        config.setTripMinDistanceMeters(150);
        config.setIsMergeEnabled(true);

        // When
        TimelinePreferences preferences = mapper.configToPreferences(config);

        // Then
        assertNotNull(preferences);
        assertEquals("claude", preferences.getStaypointDetectionAlgorithm());
        assertEquals(false, preferences.getUseVelocityAccuracy());
        assertEquals(20.0, preferences.getStaypointVelocityThreshold());
        assertEquals(150, preferences.getTripMinDistanceMeters());
        assertEquals(true, preferences.getIsMergeEnabled());
    }

    @Test
    void testNullHandling() {
        // MapStruct automatically handles null inputs

        // Given null input
        TimelinePreferences nullPreferences = null;
        UpdateTimelinePreferencesRequest nullRequest = null;
        TimelineConfig nullConfig = null;

        // When converting null inputs
        TimelineConfig configFromPrefs = mapper.preferencesToConfig(nullPreferences);
        TimelineConfig configFromRequest = mapper.requestToConfig(nullRequest);
        TimelinePreferences prefsFromConfig = mapper.configToPreferences(nullConfig);

        // Then should return null (MapStruct default behavior)
        assertNull(configFromPrefs);
        assertNull(configFromRequest);
        assertNull(prefsFromConfig);
    }

    @Test
    void testPartialData() {
        // Given preferences with only some fields set
        TimelinePreferences preferences = new TimelinePreferences();
        preferences.setStaypointDetectionAlgorithm("enhanced");
        preferences.setTripMinDistanceMeters(100);
        // Other fields are null

        // When converting
        TimelineConfig config = mapper.preferencesToConfig(preferences);

        // Then
        assertNotNull(config);
        assertEquals("enhanced", config.getStaypointDetectionAlgorithm());
        assertEquals(100, config.getTripMinDistanceMeters());
        // Other fields should be null
        assertNull(config.getUseVelocityAccuracy());
        assertNull(config.getStaypointVelocityThreshold());
        assertNull(config.getIsMergeEnabled());
    }
}