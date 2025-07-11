package org.github.tess1o.geopulse.user.mapper;

import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;
import org.github.tess1o.geopulse.user.model.UpdateTimelinePreferencesRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for MapStruct-generated TimelinePreferencesMapper.
 * This demonstrates the MapStruct solution working correctly without Quarkus context.
 */
class TimelinePreferencesMapperUnitTest {

    private final TimelinePreferencesMapper mapper = Mappers.getMapper(TimelinePreferencesMapper.class);

    @Test
    void testPreferencesToConfig() {
        TimelinePreferences preferences = TimelinePreferences.builder()
                .staypointDetectionAlgorithm("enhanced")
                .useVelocityAccuracy(true)
                .staypointVelocityThreshold(2.5)
                .staypointMaxAccuracyThreshold(50.0)
                .staypointMinAccuracyRatio(0.8)
                .tripMinDistanceMeters(100)
                .tripMinDurationMinutes(5)
                .isMergeEnabled(true)
                .mergeMaxDistanceMeters(200)
                .mergeMaxTimeGapMinutes(30)
                .tripDetectionAlgorithm("claude")
                .build();

        TimelineConfig config = mapper.preferencesToConfig(preferences);

        assertNotNull(config);
        assertEquals("enhanced", config.getStaypointDetectionAlgorithm());
        assertEquals(true, config.getUseVelocityAccuracy());
        assertEquals(2.5, config.getStaypointVelocityThreshold());
        assertEquals(50.0, config.getStaypointMaxAccuracyThreshold());
        assertEquals(0.8, config.getStaypointMinAccuracyRatio());
        assertEquals(100, config.getTripMinDistanceMeters());
        assertEquals(5, config.getTripMinDurationMinutes());
        assertEquals(true, config.getIsMergeEnabled());
        assertEquals(200, config.getMergeMaxDistanceMeters());
        assertEquals(30, config.getMergeMaxTimeGapMinutes());
        assertEquals("claude", config.getTripDetectionAlgorithm());
    }

    @Test
    void testRequestToConfig() {
        UpdateTimelinePreferencesRequest request = UpdateTimelinePreferencesRequest.builder()
                .staypointDetectionAlgorithm("original")
                .useVelocityAccuracy(false)
                .staypointVelocityThreshold(1.0)
                .tripMinDistanceMeters(75)
                .isMergeEnabled(false)
                .build();

        TimelineConfig config = mapper.requestToConfig(request);

        assertNotNull(config);
        assertEquals("original", config.getStaypointDetectionAlgorithm());
        assertEquals(false, config.getUseVelocityAccuracy());
        assertEquals(1.0, config.getStaypointVelocityThreshold());
        assertEquals(75, config.getTripMinDistanceMeters());
        assertEquals(false, config.getIsMergeEnabled());
    }

    @Test
    void testUpdatePreferencesFromConfig() {
        TimelineConfig config = TimelineConfig.builder()
                .staypointDetectionAlgorithm("claude")
                .useVelocityAccuracy(true)
                .staypointVelocityThreshold(3.0)
                .tripMinDistanceMeters(150)
                .isMergeEnabled(true)
                .mergeMaxDistanceMeters(300)
                .build();

        TimelinePreferences preferences = new TimelinePreferences();
        
        mapper.updatePreferencesFromConfig(config, preferences);

        assertEquals("claude", preferences.getStaypointDetectionAlgorithm());
        assertEquals(true, preferences.getUseVelocityAccuracy());
        assertEquals(3.0, preferences.getStaypointVelocityThreshold());
        assertEquals(150, preferences.getTripMinDistanceMeters());
        assertEquals(true, preferences.getIsMergeEnabled());
        assertEquals(300, preferences.getMergeMaxDistanceMeters());
    }

    @Test
    void testConfigToPreferences() {
        TimelineConfig config = TimelineConfig.builder()
                .staypointDetectionAlgorithm("enhanced")
                .useVelocityAccuracy(false)
                .staypointVelocityThreshold(2.0)
                .staypointMaxAccuracyThreshold(100.0)
                .tripDetectionAlgorithm("original")
                .isMergeEnabled(true)
                .mergeMaxDistanceMeters(300)
                .build();

        TimelinePreferences preferences = mapper.configToPreferences(config);

        assertNotNull(preferences);
        assertEquals("enhanced", preferences.getStaypointDetectionAlgorithm());
        assertEquals(false, preferences.getUseVelocityAccuracy());
        assertEquals(2.0, preferences.getStaypointVelocityThreshold());
        assertEquals(100.0, preferences.getStaypointMaxAccuracyThreshold());
        assertEquals("original", preferences.getTripDetectionAlgorithm());
        assertEquals(true, preferences.getIsMergeEnabled());
        assertEquals(300, preferences.getMergeMaxDistanceMeters());
    }

    @Test
    void testNullHandling() {
        assertNull(mapper.preferencesToConfig(null));
        assertNull(mapper.requestToConfig(null));
        assertNull(mapper.configToPreferences(null));
        
        TimelinePreferences preferences = new TimelinePreferences();
        mapper.updatePreferencesFromConfig(null, preferences);
        assertNull(preferences.getStaypointDetectionAlgorithm());
    }

    @Test
    void testPartialData() {
        TimelinePreferences preferences = TimelinePreferences.builder()
                .staypointDetectionAlgorithm("enhanced")
                .tripMinDistanceMeters(100)
                .build();

        TimelineConfig config = mapper.preferencesToConfig(preferences);

        assertNotNull(config);
        assertEquals("enhanced", config.getStaypointDetectionAlgorithm());
        assertEquals(100, config.getTripMinDistanceMeters());
        assertNull(config.getUseVelocityAccuracy());
        assertNull(config.getStaypointVelocityThreshold());
        assertNull(config.getIsMergeEnabled());
    }
}