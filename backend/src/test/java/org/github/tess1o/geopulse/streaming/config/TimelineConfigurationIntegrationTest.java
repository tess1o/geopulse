package org.github.tess1o.geopulse.streaming.config;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.db.PostgisTestResource;
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
    @Inject
    CleanupHelper cleanupHelper;

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
        cleanupHelper.cleanupTimeline();
        userRepository.deleteById(testUser.getId());
    }

    @Test
    @Transactional
    void testCompleteFlow_PropertiesToDefaultToUserToEffective() {
        // Step 1: Verify global default config loads from properties
        TimelineConfig defaultConfig = globalTimelineConfig.getDefaultTimelineConfig();

        log.info("Default config: {}", defaultConfig);

        assertEquals(true, defaultConfig.getUseVelocityAccuracy());
        assertEquals(20.0, defaultConfig.getStaypointVelocityThreshold());
        assertEquals(40.0, defaultConfig.getStaypointMaxAccuracyThreshold());
        assertEquals(200, defaultConfig.getStaypointRadiusMeters());

        // Step 2: Initially, user has no preferences, so effective config = default
        TimelineConfig initialEffective = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());

        assertEquals(true, initialEffective.getUseVelocityAccuracy());
        assertEquals(20.0, initialEffective.getStaypointVelocityThreshold());
        assertEquals(200, initialEffective.getStaypointRadiusMeters());

        // Step 3: User updates some preferences
        UpdateTimelinePreferencesRequest updateRequest = new UpdateTimelinePreferencesRequest();
        updateRequest.setStaypointVelocityThreshold(25.0);
        updateRequest.setStaypointRadiusMeters(150);
        // Leave other fields null to keep defaults

        userService.updateTimelinePreferences(testUser.getId(), updateRequest);

        // Force flush to database and clear persistence context
        entityManager.flush();
        entityManager.clear();

        // Step 4: Verify effective config now combines user preferences with defaults
        testUser = userRepository.findById(testUser.getId()); // Refresh from DB
        TimelineConfig finalEffective = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());

        // User overrides should be applied
        assertEquals(25.0, finalEffective.getStaypointVelocityThreshold());
        assertEquals(150, finalEffective.getStaypointRadiusMeters());

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
        user1Prefs.setStaypointRadiusMeters(100);

        UpdateTimelinePreferencesRequest user2Prefs = new UpdateTimelinePreferencesRequest();
        user2Prefs.setStaypointRadiusMeters(300);

        userService.updateTimelinePreferences(testUser.getId(), user1Prefs);
        userService.updateTimelinePreferences(user2.getId(), user2Prefs);

        // Verify each user gets their own effective configuration
        testUser = userRepository.findById(testUser.getId());
        user2 = userRepository.findById(user2.getId());

        TimelineConfig user1Config = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());
        TimelineConfig user2Config = timelineConfigurationProvider.getConfigurationForUser(user2.getId());

        // Users should have different overrides
        assertEquals(100, user1Config.getStaypointRadiusMeters());

        assertEquals(300, user2Config.getStaypointRadiusMeters());

        // But same defaults for non-overridden fields
        assertEquals(user1Config.getUseVelocityAccuracy(), user2Config.getUseVelocityAccuracy());
        assertEquals(user1Config.getStaypointMaxAccuracyThreshold(), user2Config.getStaypointMaxAccuracyThreshold());
    }

    @Test
    @Transactional
    void testConfigurationUpdateFlow() {
        // Initial state: no preferences
        TimelineConfig initialConfig = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());

        // Update 1: Set some preferences
        UpdateTimelinePreferencesRequest update1 = new UpdateTimelinePreferencesRequest();
        update1.setStaypointRadiusMeters(100);

        userService.updateTimelinePreferences(testUser.getId(), update1);
        testUser = userRepository.findById(testUser.getId());

        TimelineConfig afterUpdate1 = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());
        assertEquals(100, afterUpdate1.getStaypointRadiusMeters());

        // Update 2: Modify existing preferences
        UpdateTimelinePreferencesRequest update2 = new UpdateTimelinePreferencesRequest();
        update2.setUseVelocityAccuracy(false); // New field
        // Don't modify staypointRadiusMeters - should be preserved

        userService.updateTimelinePreferences(testUser.getId(), update2);
        testUser = userRepository.findById(testUser.getId());

        TimelineConfig afterUpdate2 = timelineConfigurationProvider.getConfigurationForUser(testUser.getId());
        assertEquals(false, afterUpdate2.getUseVelocityAccuracy()); // New
        assertEquals(100, afterUpdate2.getStaypointRadiusMeters()); // Preserved
    }

    // Test profile with custom configuration values for integration testing
    public static class TestConfigProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.ofEntries(
                    java.util.Map.entry("geopulse.timeline.staypoint.use_velocity_accuracy", "true"),
                    java.util.Map.entry("geopulse.timeline.staypoint.velocity.threshold", "20.0"),
                    java.util.Map.entry("geopulse.timeline.staypoint.accuracy.threshold", "40.0"),
                    java.util.Map.entry("geopulse.timeline.staypoint.min_accuracy_ratio", "0.7"),
                    java.util.Map.entry("geopulse.timeline.trip.detection.algorithm", "test_trip"),
                    java.util.Map.entry("geopulse.timeline.staypoint.radius_meters", "200"),
                    java.util.Map.entry("geopulse.timeline.staypoint.min_duration_minutes", "25"),
                    java.util.Map.entry("geopulse.timeline.staypoint.merge.enabled", "true"),
                    java.util.Map.entry("geopulse.timeline.staypoint.merge.max_distance_meters", "400"),
                    java.util.Map.entry("geopulse.timeline.staypoint.merge.max_time_gap_minutes", "35")
            );
        }
    }
}