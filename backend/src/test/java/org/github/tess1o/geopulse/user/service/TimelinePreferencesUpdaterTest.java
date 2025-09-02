package org.github.tess1o.geopulse.user.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;
import org.github.tess1o.geopulse.user.model.UpdateTimelinePreferencesRequest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
class TimelinePreferencesUpdaterTest {

    @Inject
    UserService userService;

    @Inject
    UserRepository userRepository;

    private UserEntity testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        // Create test user with existing timeline preferences
        testUser = new UserEntity();
        testUser.setEmail("TimelinePreferencesUpdaterTest@example.com");
        testUser.setFullName("Test User");
        testUser.setPasswordHash("hashedpassword");
        
        TimelinePreferences existingPrefs = new TimelinePreferences();
        existingPrefs.setUseVelocityAccuracy(true);
        existingPrefs.setStaypointVelocityThreshold(8.0);
        existingPrefs.setStaypointRadiusMeters(50);
        existingPrefs.setIsMergeEnabled(true);
        
        testUser.setTimelinePreferences(existingPrefs);
        userRepository.persist(testUser);
    }

    @AfterEach
    @Transactional
    void cleanup() {
        userRepository.deleteById(testUser.getId());
    }

    @Test
    @Transactional
    void testUpdateTimelinePreferences_PartialUpdate() {
        UpdateTimelinePreferencesRequest request = new UpdateTimelinePreferencesRequest();
        request.setStaypointVelocityThreshold(12.0); // Update this
        request.setIsMergeEnabled(false); // Update this
        // Leave other fields null - should keep existing values

        userService.updateTimelinePreferences(testUser.getId(), request);

        // Refresh user from database
        testUser = userRepository.findById(testUser.getId());
        TimelinePreferences updatedPrefs = testUser.getTimelinePreferences();

        assertNotNull(updatedPrefs);
        
        // Check updated fields
        assertEquals(12.0, updatedPrefs.getStaypointVelocityThreshold());
        assertEquals(false, updatedPrefs.getIsMergeEnabled());
        
        // Check preserved fields
        assertEquals(true, updatedPrefs.getUseVelocityAccuracy());
        assertEquals(50, updatedPrefs.getStaypointRadiusMeters());
    }

    @Test
    @Transactional
    void testUpdateTimelinePreferences_CompleteUpdate() {
        UpdateTimelinePreferencesRequest request = new UpdateTimelinePreferencesRequest();
        request.setUseVelocityAccuracy(false);
        request.setStaypointVelocityThreshold(15.0);
        request.setStaypointMaxAccuracyThreshold(25.0);
        request.setStaypointMinAccuracyRatio(0.9);
        request.setTripDetectionAlgorithm("single");
        request.setStaypointRadiusMeters(75);
        request.setStaypointMinDurationMinutes(10);
        request.setIsMergeEnabled(false);
        request.setMergeMaxDistanceMeters(200);
        request.setMergeMaxTimeGapMinutes(30);

        userService.updateTimelinePreferences(testUser.getId(), request);

        // Refresh user from database
        testUser = userRepository.findById(testUser.getId());
        TimelinePreferences updatedPrefs = testUser.getTimelinePreferences();

        assertNotNull(updatedPrefs);
        
        // All fields should be updated
        assertEquals(false, updatedPrefs.getUseVelocityAccuracy());
        assertEquals(15.0, updatedPrefs.getStaypointVelocityThreshold());
        assertEquals(25.0, updatedPrefs.getStaypointMaxAccuracyThreshold());
        assertEquals(0.9, updatedPrefs.getStaypointMinAccuracyRatio());
        assertEquals("single", updatedPrefs.getTripDetectionAlgorithm());
        assertEquals(75, updatedPrefs.getStaypointRadiusMeters());
        assertEquals(10, updatedPrefs.getStaypointMinDurationMinutes());
        assertEquals(false, updatedPrefs.getIsMergeEnabled());
        assertEquals(200, updatedPrefs.getMergeMaxDistanceMeters());
        assertEquals(30, updatedPrefs.getMergeMaxTimeGapMinutes());
    }

    @Test
    @Transactional
    void testUpdateTimelinePreferences_EmptyUpdate() {
        UpdateTimelinePreferencesRequest request = new UpdateTimelinePreferencesRequest();
        // All fields null - should preserve existing values

        TimelinePreferences originalPrefs = testUser.getTimelinePreferences();
        
        userService.updateTimelinePreferences(testUser.getId(), request);

        // Refresh user from database
        testUser = userRepository.findById(testUser.getId());
        TimelinePreferences updatedPrefs = testUser.getTimelinePreferences();

        assertNotNull(updatedPrefs);
        
        // All fields should remain unchanged
        assertEquals(originalPrefs.getUseVelocityAccuracy(), updatedPrefs.getUseVelocityAccuracy());
        assertEquals(originalPrefs.getStaypointVelocityThreshold(), updatedPrefs.getStaypointVelocityThreshold());
        assertEquals(originalPrefs.getStaypointRadiusMeters(), updatedPrefs.getStaypointRadiusMeters());
        assertEquals(originalPrefs.getIsMergeEnabled(), updatedPrefs.getIsMergeEnabled());
    }

    @Test
    @Transactional
    void testUpdateTimelinePreferences_CreatesPreferencesIfNotExists() {
        // Create user without timeline preferences
        UserEntity userWithoutPrefs = new UserEntity();
        userWithoutPrefs.setEmail("nopref@example.com");
        userWithoutPrefs.setFullName("No Prefs User");
        userWithoutPrefs.setPasswordHash("hashedpassword");
        userWithoutPrefs.setTimelinePreferences(null);
        userRepository.persist(userWithoutPrefs);

        UpdateTimelinePreferencesRequest request = new UpdateTimelinePreferencesRequest();
        request.setStaypointRadiusMeters(100);

        userService.updateTimelinePreferences(userWithoutPrefs.getId(), request);

        // Refresh user from database
        userWithoutPrefs = userRepository.findById(userWithoutPrefs.getId());
        TimelinePreferences createdPrefs = userWithoutPrefs.getTimelinePreferences();

        assertNotNull(createdPrefs);
        assertEquals(100, createdPrefs.getStaypointRadiusMeters());
        
        // Other fields should be null (no defaults set in preferences)
        assertNull(createdPrefs.getUseVelocityAccuracy());
        assertNull(createdPrefs.getStaypointVelocityThreshold());
    }

    @Test
    @Transactional
    void testUpdateTimelinePreferences_OverwritesWithNullableFields() {
        UpdateTimelinePreferencesRequest request = new UpdateTimelinePreferencesRequest();
        request.setUseVelocityAccuracy(null); // Explicitly set to null
        request.setStaypointVelocityThreshold(10.0);

        userService.updateTimelinePreferences(testUser.getId(), request);

        // Refresh user from database
        testUser = userRepository.findById(testUser.getId());
        TimelinePreferences updatedPrefs = testUser.getTimelinePreferences();

        assertNotNull(updatedPrefs);
        
        // Updated field
        assertEquals(10.0, updatedPrefs.getStaypointVelocityThreshold());
        
        // Preserved field (null in request means don't update)
        assertEquals(true, updatedPrefs.getUseVelocityAccuracy());
        assertEquals(50, updatedPrefs.getStaypointRadiusMeters());
    }

    @Test
    @Transactional
    void testUpdateTimelinePreferences_PersistsChanges() {
        UpdateTimelinePreferencesRequest request = new UpdateTimelinePreferencesRequest();
        request.setStaypointRadiusMeters(999);

        userService.updateTimelinePreferences(testUser.getId(), request);

        // Create new transaction to verify persistence
        UserEntity freshUser = userRepository.findById(testUser.getId());
        TimelinePreferences persistedPrefs = freshUser.getTimelinePreferences();

        assertNotNull(persistedPrefs);
        assertEquals(999, persistedPrefs.getStaypointRadiusMeters());
    }
}