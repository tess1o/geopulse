package org.github.tess1o.geopulse.timeline.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.config.GlobalTimelineConfig;
import org.github.tess1o.geopulse.timeline.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.user.model.UpdateTimelinePreferencesRequest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test for the complete timeline configuration flow:
 * Properties → Global Config → User Preferences → Effective Config
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@TestProfile(TimelineConfigurationIntegrationTest.TestConfigProfile.class)
@Slf4j
class TimelineConfigurationIntegrationTest {

    @Inject
    GlobalTimelineConfig globalTimelineConfig;

    @Inject
    TimelineConfigurationProvider timelineConfigurationProvider;

    @Inject
    UserService userService;

    @Inject
    UserRepository userRepository;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    private UserEntity testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        testUser = new UserEntity();
        testUser.setEmail("integration@example.com");
        testUser.setFullName("Integration User");
        testUser.setPasswordHash("hashedpassword");
        userRepository.persist(testUser);
    }

    @AfterEach
    @Transactional
    void cleanup() {
        userRepository.deleteById(testUser.getId());
    }

    @Test
    @Transactional
    void testCompleteFlow_PropertiesToDefaultToUserToEffective() {
        // Step 1: Verify global default config loads from properties
        TimelineConfig defaultConfig = globalTimelineConfig.getDefaultTimelineConfig();

        log.info("Default config: {}", defaultConfig);

        assertEquals("test_algorithm", defaultConfig.getStaypointDetectionAlgorithm());
        assertEquals(true, defaultConfig.getUseVelocityAccuracy());
        assertEquals(20.0, defaultConfig.getStaypointVelocityThreshold());
        assertEquals(40.0, defaultConfig.getStaypointMaxAccuracyThreshold());
        assertEquals(200, defaultConfig.getTripMinDistanceMeters());

        // Step 2: Initially, user has no preferences, so effective config = default
        TimelineConfig initialEffective = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());

        assertEquals("test_algorithm", initialEffective.getStaypointDetectionAlgorithm());
        assertEquals(true, initialEffective.getUseVelocityAccuracy());
        assertEquals(20.0, initialEffective.getStaypointVelocityThreshold());
        assertEquals(200, initialEffective.getTripMinDistanceMeters());

        // Step 3: User updates some preferences
        UpdateTimelinePreferencesRequest updateRequest = new UpdateTimelinePreferencesRequest();
        updateRequest.setStaypointDetectionAlgorithm("user_custom");
        updateRequest.setStaypointVelocityThreshold(25.0);
        updateRequest.setTripMinDistanceMeters(150);
        // Leave other fields null to keep defaults

        userService.updateTimelinePreferences(testUser.getId(), updateRequest);

        // Force flush to database and clear persistence context
        entityManager.flush();
        entityManager.clear();

        // Step 4: Verify effective config now combines user preferences with defaults
        testUser = userRepository.findById(testUser.getId()); // Refresh from DB
        TimelineConfig finalEffective = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());

        // User overrides should be applied
        assertEquals("user_custom", finalEffective.getStaypointDetectionAlgorithm());
        assertEquals(25.0, finalEffective.getStaypointVelocityThreshold());
        assertEquals(150, finalEffective.getTripMinDistanceMeters());

        // Default values should be preserved for non-overridden fields
        assertEquals(true, finalEffective.getUseVelocityAccuracy());
        assertEquals(40.0, finalEffective.getStaypointMaxAccuracyThreshold());
    }

    @Test
    @Transactional
    void testUserPreferencesPersistenceAndIsolation() {
        // Create second user
        UserEntity user2 = new UserEntity();
        user2.setEmail("user2@example.com");
        user2.setFullName("User Two");
        user2.setPasswordHash("hashedpassword");
        userRepository.persist(user2);

        // Set different preferences for each user
        UpdateTimelinePreferencesRequest user1Prefs = new UpdateTimelinePreferencesRequest();
        user1Prefs.setStaypointDetectionAlgorithm("user1_algo");
        user1Prefs.setTripMinDistanceMeters(100);

        UpdateTimelinePreferencesRequest user2Prefs = new UpdateTimelinePreferencesRequest();
        user2Prefs.setStaypointDetectionAlgorithm("user2_algo");
        user2Prefs.setTripMinDistanceMeters(300);

        userService.updateTimelinePreferences(testUser.getId(), user1Prefs);
        userService.updateTimelinePreferences(user2.getId(), user2Prefs);

        // Verify each user gets their own effective configuration
        testUser = userRepository.findById(testUser.getId());
        user2 = userRepository.findById(user2.getId());

        TimelineConfig user1Config = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());
        TimelineConfig user2Config = timelineConfigurationProvider.getConfigurationForUser(user2.getId());

        // Users should have different overrides
        assertEquals("user1_algo", user1Config.getStaypointDetectionAlgorithm());
        assertEquals(100, user1Config.getTripMinDistanceMeters());

        assertEquals("user2_algo", user2Config.getStaypointDetectionAlgorithm());
        assertEquals(300, user2Config.getTripMinDistanceMeters());

        // But same defaults for non-overridden fields
        assertEquals(user1Config.getUseVelocityAccuracy(), user2Config.getUseVelocityAccuracy());
        assertEquals(user1Config.getStaypointMaxAccuracyThreshold(), user2Config.getStaypointMaxAccuracyThreshold());
    }

    @Test
    @Transactional
    void testConfigurationUpdateFlow() {
        // Initial state: no preferences
        TimelineConfig initialConfig = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());
        assertEquals("test_algorithm", initialConfig.getStaypointDetectionAlgorithm());

        // Update 1: Set some preferences
        UpdateTimelinePreferencesRequest update1 = new UpdateTimelinePreferencesRequest();
        update1.setStaypointDetectionAlgorithm("version1");
        update1.setTripMinDistanceMeters(100);

        userService.updateTimelinePreferences(testUser.getId(), update1);
        testUser = userRepository.findById(testUser.getId());

        TimelineConfig afterUpdate1 = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());
        assertEquals("version1", afterUpdate1.getStaypointDetectionAlgorithm());
        assertEquals(100, afterUpdate1.getTripMinDistanceMeters());

        // Update 2: Modify existing preferences
        UpdateTimelinePreferencesRequest update2 = new UpdateTimelinePreferencesRequest();
        update2.setStaypointDetectionAlgorithm("version2");
        update2.setUseVelocityAccuracy(false); // New field
        // Don't modify tripMinDistanceMeters - should be preserved

        userService.updateTimelinePreferences(testUser.getId(), update2);
        testUser = userRepository.findById(testUser.getId());

        TimelineConfig afterUpdate2 = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());
        assertEquals("version2", afterUpdate2.getStaypointDetectionAlgorithm()); // Updated
        assertEquals(false, afterUpdate2.getUseVelocityAccuracy()); // New
        assertEquals(100, afterUpdate2.getTripMinDistanceMeters()); // Preserved
    }

    @Test
    void testDefaultConfigurationImmutability() {
        TimelineConfig defaultConfig1 = globalTimelineConfig.getDefaultTimelineConfig();
        TimelineConfig defaultConfig2 = globalTimelineConfig.getDefaultTimelineConfig();

        // Should be different instances
        assertNotSame(defaultConfig1, defaultConfig2);

        // Modifying one should not affect the other
        defaultConfig1.setStaypointDetectionAlgorithm("modified");
        assertEquals("test_algorithm", defaultConfig2.getStaypointDetectionAlgorithm());

        // Getting a new default should not be affected
        TimelineConfig defaultConfig3 = globalTimelineConfig.getDefaultTimelineConfig();
        assertEquals("test_algorithm", defaultConfig3.getStaypointDetectionAlgorithm());
    }

    @Test
    void testEffectiveConfigurationImmutability() {
        TimelineConfig effective1 = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());
        TimelineConfig effective2 = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());

        // Should be different instances
        assertNotSame(effective1, effective2);

        // Modifying one should not affect subsequent calls
        effective1.setStaypointDetectionAlgorithm("modified");
        assertEquals("test_algorithm", effective2.getStaypointDetectionAlgorithm());
    }

    // Test profile with custom configuration values for integration testing
    public static class TestConfigProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.ofEntries(
                    java.util.Map.entry(
                            "geopulse.timeline.staypoint.detection.algorithm", "test_algorithm"),
                    java.util.Map.entry("geopulse.timeline.staypoint.use_velocity_accuracy", "true"),
                    java.util.Map.entry("geopulse.timeline.staypoint.velocity.threshold", "20.0"),
                    java.util.Map.entry("geopulse.timeline.staypoint.accuracy.threshold", "40.0"),
                    java.util.Map.entry("geopulse.timeline.staypoint.min_accuracy_ratio", "0.7"),
                    java.util.Map.entry("geopulse.timeline.trip.detection.algorithm", "test_trip"),
                    java.util.Map.entry("geopulse.timeline.trip.min_distance_meters", "200"),
                    java.util.Map.entry("geopulse.timeline.trip.min_duration_minutes", "25"),
                    java.util.Map.entry("geopulse.timeline.staypoint.merge.enabled", "true"),
                    java.util.Map.entry("geopulse.timeline.staypoint.merge.max_distance_meters", "400"),
                    java.util.Map.entry("geopulse.timeline.staypoint.merge.max_time_gap_minutes", "35")
            );
        }
    }
}