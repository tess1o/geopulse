package org.github.tess1o.geopulse.user.mapper;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;
import org.github.tess1o.geopulse.user.model.UpdateTimelinePreferencesRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class TimelinePreferencesMapperTest {
    private final TimelinePreferencesMapper mapper = Mappers.getMapper(TimelinePreferencesMapper.class);
    @Test
    void testPreferencesToConfig() {
        // Given
        TimelinePreferences preferences = new TimelinePreferences();
        preferences.setUseVelocityAccuracy(true);
        preferences.setStaypointVelocityThreshold(12.0);
        preferences.setStaypointRadiusMeters(100);
        preferences.setIsMergeEnabled(false);
        preferences.setCarEnabled(false);
        // When
        TimelineConfig config = mapper.preferencesToConfig(preferences);
        // Then
        assertNotNull(config);
        assertEquals(true, config.getUseVelocityAccuracy());
        assertEquals(12.0, config.getStaypointVelocityThreshold());
        assertEquals(100, config.getStaypointRadiusMeters());
        assertEquals(false, config.getIsMergeEnabled());
        assertEquals(false, config.getCarEnabled());
    }
    @Test
    void testRequestToConfig() {
        // Given
        UpdateTimelinePreferencesRequest request = new UpdateTimelinePreferencesRequest();
        request.setStaypointVelocityThreshold(15.0);
        request.setStaypointRadiusMeters(200);
        request.setIsMergeEnabled(true);
        request.setCarEnabled(false);
        // When
        TimelineConfig config = mapper.requestToConfig(request);
        // Then
        assertNotNull(config);
        assertEquals(15.0, config.getStaypointVelocityThreshold());
        assertEquals(200, config.getStaypointRadiusMeters());
        assertEquals(true, config.getIsMergeEnabled());
        assertEquals(false, config.getCarEnabled());
    }
    @Test
    void testUpdatePreferencesFromConfig() {
        // Given
        TimelinePreferences preferences = new TimelinePreferences();
        preferences.setStaypointRadiusMeters(50);
        TimelineConfig config = new TimelineConfig();
        config.setUseVelocityAccuracy(true);
        config.setStaypointVelocityThreshold(12.0);
        config.setStaypointRadiusMeters(100);
        config.setIsMergeEnabled(false);
        config.setCarEnabled(false);
        // When
        mapper.updatePreferencesFromConfig(config, preferences);
        // Then
        assertEquals(true, preferences.getUseVelocityAccuracy());
        assertEquals(12.0, preferences.getStaypointVelocityThreshold());
        assertEquals(100, preferences.getStaypointRadiusMeters());
        assertEquals(false, preferences.getIsMergeEnabled());
        assertEquals(false, preferences.getCarEnabled());
    }
    @Test
    void testConfigToPreferences() {
        // Given
        TimelineConfig config = new TimelineConfig();
        config.setUseVelocityAccuracy(false);
        config.setStaypointVelocityThreshold(20.0);
        config.setStaypointRadiusMeters(150);
        config.setIsMergeEnabled(true);
        config.setCarEnabled(true);
        // When
        TimelinePreferences preferences = mapper.configToPreferences(config);
        // Then
        assertNotNull(preferences);
        assertEquals(false, preferences.getUseVelocityAccuracy());
        assertEquals(20.0, preferences.getStaypointVelocityThreshold());
        assertEquals(150, preferences.getStaypointRadiusMeters());
        assertEquals(true, preferences.getIsMergeEnabled());
        assertEquals(true, preferences.getCarEnabled());
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
        preferences.setStaypointRadiusMeters(100);
        // Other fields are null
        // When converting
        TimelineConfig config = mapper.preferencesToConfig(preferences);
        // Then
        assertNotNull(config);
        assertEquals(100, config.getStaypointRadiusMeters());
        // Other fields should be null
        assertNull(config.getUseVelocityAccuracy());
        assertNull(config.getStaypointVelocityThreshold());
        assertNull(config.getIsMergeEnabled());
    }
}
