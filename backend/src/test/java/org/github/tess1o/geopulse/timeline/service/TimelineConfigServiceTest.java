package org.github.tess1o.geopulse.timeline.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.timeline.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(TimelineConfigServiceTest.TestTimelineConfigProfile.class)
@QuarkusTestResource(PostgisTestResource.class)
class TimelineConfigServiceTest {

    @Inject
    TimelineConfigurationProvider timelineConfigurationProvider;

    @Inject
    UserRepository userRepository;

    private UserEntity testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        // Create test user with timeline preferences
        testUser = new UserEntity();
        testUser.setEmail("test@example.com");
        testUser.setFullName("Test User");
        testUser.setPasswordHash("hashedpassword");
        
        TimelinePreferences preferences = new TimelinePreferences();
        preferences.setStaypointDetectionAlgorithm("enhanced");
        preferences.setStaypointVelocityThreshold(12.0);
        preferences.setTripMinDistanceMeters(100);
        preferences.setIsMergeEnabled(false);
        // Leave other fields null to test partial preferences
        
        testUser.setTimelinePreferences(preferences);
        userRepository.persist(testUser);
    }

    @AfterEach
    @Transactional
    void cleanup() {
        userRepository.deleteById(testUser.getId());
    }

    @Test
    void testGetEffectiveTimelineConfig_WithUserPreferences() {
        TimelineConfig effectiveConfig = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());

        assertNotNull(effectiveConfig);
        
        // Test overridden values from user preferences
        assertEquals("enhanced", effectiveConfig.getStaypointDetectionAlgorithm());
        assertEquals(12.0, effectiveConfig.getStaypointVelocityThreshold());
        assertEquals(100, effectiveConfig.getTripMinDistanceMeters());
        assertEquals(false, effectiveConfig.getIsMergeEnabled());
        
        // Test default values for non-overridden fields (from test profile)
        assertEquals(false, effectiveConfig.getUseVelocityAccuracy());
        assertEquals(30.0, effectiveConfig.getStaypointMaxAccuracyThreshold());
        assertEquals(0.8, effectiveConfig.getStaypointMinAccuracyRatio());
        assertEquals("multiple", effectiveConfig.getTripDetectionAlgorithm());
        assertEquals(15, effectiveConfig.getTripMinDurationMinutes());
        assertEquals(300, effectiveConfig.getMergeMaxDistanceMeters());
        assertEquals(20, effectiveConfig.getMergeMaxTimeGapMinutes());
    }

    @Test
    @Transactional
    void testGetEffectiveTimelineConfig_WithoutUserPreferences() {
        // Create user without timeline preferences
        UserEntity userWithoutPrefs = new UserEntity();
        userWithoutPrefs.setEmail("nopref@example.com");
        userWithoutPrefs.setFullName("No Prefs User");
        userWithoutPrefs.setPasswordHash("hashedpassword");
        userWithoutPrefs.setTimelinePreferences(null);
        userRepository.persist(userWithoutPrefs);

        TimelineConfig effectiveConfig = timelineConfigurationProvider.getConfigurationForUser(userWithoutPrefs.getId());

        assertNotNull(effectiveConfig);
        
        // Should use all default values from test profile
        assertEquals("enhanced", effectiveConfig.getStaypointDetectionAlgorithm());
        assertEquals(false, effectiveConfig.getUseVelocityAccuracy());
        assertEquals(15.0, effectiveConfig.getStaypointVelocityThreshold());
        assertEquals(30.0, effectiveConfig.getStaypointMaxAccuracyThreshold());
        assertEquals(0.8, effectiveConfig.getStaypointMinAccuracyRatio());
        assertEquals("multiple", effectiveConfig.getTripDetectionAlgorithm());
        assertEquals(100, effectiveConfig.getTripMinDistanceMeters());
        assertEquals(15, effectiveConfig.getTripMinDurationMinutes());
        assertEquals(false, effectiveConfig.getIsMergeEnabled());
        assertEquals(300, effectiveConfig.getMergeMaxDistanceMeters());
        assertEquals(20, effectiveConfig.getMergeMaxTimeGapMinutes());
    }

    @Test
    @Transactional
    void testGetEffectiveTimelineConfig_WithEmptyPreferences() {
        // Create user with empty timeline preferences (all fields null)
        UserEntity userWithEmptyPrefs = new UserEntity();
        userWithEmptyPrefs.setEmail("empty@example.com");
        userWithEmptyPrefs.setFullName("Empty Prefs User");
        userWithEmptyPrefs.setPasswordHash("hashedpassword");
        userWithEmptyPrefs.setTimelinePreferences(new TimelinePreferences());
        userRepository.persist(userWithEmptyPrefs);

        TimelineConfig effectiveConfig = timelineConfigurationProvider.getConfigurationForUser(userWithEmptyPrefs.getId());

        assertNotNull(effectiveConfig);
        
        // Should use all default values since preferences are empty
        assertEquals("enhanced", effectiveConfig.getStaypointDetectionAlgorithm());
        assertEquals(false, effectiveConfig.getUseVelocityAccuracy());
        assertEquals(15.0, effectiveConfig.getStaypointVelocityThreshold());
    }

    @Test
    void testGetEffectiveTimelineConfig_ReturnsNewInstanceEachTime() {
        TimelineConfig config1 = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());
        TimelineConfig config2 = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());

        assertNotSame(config1, config2);
        assertEquals(config1.getStaypointDetectionAlgorithm(), config2.getStaypointDetectionAlgorithm());
    }

    @Test
    void testGetEffectiveTimelineConfig_ModificationDoesNotAffectOriginal() {
        TimelineConfig effectiveConfig = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());
        
        // Modify the returned config
        effectiveConfig.setStaypointDetectionAlgorithm("modified");
        effectiveConfig.setTripMinDistanceMeters(999);
        
        // Get another config instance
        TimelineConfig newConfig = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());
        
        // Should not be affected by modifications to the previous instance
        assertEquals("enhanced", newConfig.getStaypointDetectionAlgorithm());
        assertEquals(100, newConfig.getTripMinDistanceMeters());
    }

    // Test profile with custom configuration values
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