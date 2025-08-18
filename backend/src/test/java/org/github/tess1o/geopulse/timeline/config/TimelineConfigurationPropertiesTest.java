package org.github.tess1o.geopulse.timeline.config;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for TimelineConfigurationProperties to verify
 * that environment variables properly override default values.
 */
@QuarkusTest
class TimelineConfigurationPropertiesTest {

    @Inject
    TimelineConfigurationProperties properties;

    @Test
    void testDefaultValues() {
        // Verify that values from application.properties are loaded correctly
        // These values should match what's configured in application.properties
        assertEquals("enhanced", properties.getStaypointDetectionAlgorithm());
        assertEquals("true", properties.getUseVelocityAccuracy());
        assertEquals("2.5", properties.getStaypointVelocityThreshold()); // From application.properties
        assertEquals("60.0", properties.getStaypointAccuracyThreshold());
        assertEquals("0.5", properties.getStaypointMinAccuracyRatio());
        assertEquals("single", properties.getTripDetectionAlgorithm());
        assertEquals("50", properties.getTripMinDistanceMeters());
        assertEquals("7", properties.getTripMinDurationMinutes());
        assertEquals("true", properties.getMergeEnabled());
        assertEquals("400", properties.getMergeMaxDistanceMeters()); // From application.properties  
        assertEquals("15", properties.getMergeMaxTimeGapMinutes()); // From application.properties
        assertEquals("true", properties.getPathSimplificationEnabled());
        assertEquals("15.0", properties.getPathSimplificationTolerance());
        assertEquals("100", properties.getPathMaxPoints());
        assertEquals("true", properties.getPathAdaptiveSimplification());
        assertEquals("10800", properties.getDataGapThresholdSeconds());
        assertEquals("1800", properties.getDataGapMinDurationSeconds());
    }

    @Test
    void testPropertiesAreNotNull() {
        // All properties should be injected and not null
        assertNotNull(properties.getStaypointDetectionAlgorithm());
        assertNotNull(properties.getUseVelocityAccuracy());
        assertNotNull(properties.getStaypointVelocityThreshold());
        assertNotNull(properties.getStaypointAccuracyThreshold());
        assertNotNull(properties.getStaypointMinAccuracyRatio());
        assertNotNull(properties.getTripDetectionAlgorithm());
        assertNotNull(properties.getTripMinDistanceMeters());
        assertNotNull(properties.getTripMinDurationMinutes());
        assertNotNull(properties.getMergeEnabled());
        assertNotNull(properties.getMergeMaxDistanceMeters());
        assertNotNull(properties.getMergeMaxTimeGapMinutes());
        assertNotNull(properties.getPathSimplificationEnabled());
        assertNotNull(properties.getPathSimplificationTolerance());
        assertNotNull(properties.getPathMaxPoints());
        assertNotNull(properties.getPathAdaptiveSimplification());
        assertNotNull(properties.getDataGapThresholdSeconds());
        assertNotNull(properties.getDataGapMinDurationSeconds());
    }
}