package org.github.tess1o.geopulse.timeline.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.timeline.config.GlobalTimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(GlobalTimelineConfigTest.TestTimelineConfigProfile.class)
class GlobalTimelineConfigTest {

    @Inject
    GlobalTimelineConfig globalTimelineConfig;

    @Test
    void testGetDefaultTimelineConfig_LoadsFromProperties() {
        TimelineConfig config = globalTimelineConfig.getDefaultTimelineConfig();

        assertNotNull(config);
        
        // Test properties are loaded from test profile
        assertEquals("enhanced", config.getStaypointDetectionAlgorithm());
        assertEquals(false, config.getUseVelocityAccuracy());
        assertEquals(15.0, config.getStaypointVelocityThreshold());
        assertEquals(30.0, config.getStaypointMaxAccuracyThreshold());
        assertEquals(0.8, config.getStaypointMinAccuracyRatio());
        assertEquals("multiple", config.getTripDetectionAlgorithm());
        assertEquals(100, config.getTripMinDistanceMeters());
        assertEquals(15, config.getTripMinDurationMinutes());
        assertEquals(false, config.getIsMergeEnabled());
        assertEquals(300, config.getMergeMaxDistanceMeters());
        assertEquals(20, config.getMergeMaxTimeGapMinutes());
    }

    @Test
    void testGetDefaultTimelineConfig_ReturnsNewInstanceEachTime() {
        TimelineConfig config1 = globalTimelineConfig.getDefaultTimelineConfig();
        TimelineConfig config2 = globalTimelineConfig.getDefaultTimelineConfig();

        assertNotSame(config1, config2);
        assertEquals(config1.getStaypointDetectionAlgorithm(), config2.getStaypointDetectionAlgorithm());
    }

    // Test profile to provide custom configuration values
    public static class TestTimelineConfigProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.ofEntries(
                java.util.Map.entry(
"geopulse.timeline.staypoint.detection.algorithm", "enhanced"),
                java.util.Map.entry("geopulse.timeline.staypoint.use_velocity_accuracy", "false"),
                java.util.Map.entry("geopulse.timeline.staypoint.velocity.threshold", "15.0"),
                java.util.Map.entry("geopulse.timeline.staypoint.accuracy.threshold", "30.0"),
                java.util.Map.entry("geopulse.timeline.staypoint.min_accuracy_ratio", "0.8"),
                java.util.Map.entry("geopulse.timeline.trip.detection.algorithm", "multiple"),
                java.util.Map.entry("geopulse.timeline.trip.min_distance_meters", "100"),
                java.util.Map.entry("geopulse.timeline.trip.min_duration_minutes", "15"),
                java.util.Map.entry("geopulse.timeline.staypoint.merge.enabled", "false"),
                java.util.Map.entry("geopulse.timeline.staypoint.merge.max_distance_meters", "300"),
                java.util.Map.entry("geopulse.timeline.staypoint.merge.max_time_gap_minutes", "20")
            );
        }
    }
}