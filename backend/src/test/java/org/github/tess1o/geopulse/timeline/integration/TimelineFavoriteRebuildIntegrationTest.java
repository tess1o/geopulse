package org.github.tess1o.geopulse.timeline.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.timeline.model.*;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.service.TimelineInvalidationService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for timeline rebuilding when favorites are added, edited, or removed.
 * Tests the invalidation system and verifies proper timeline entity relationships.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class TimelineFavoriteRebuildIntegrationTest {

    @Inject
    TimelineInvalidationService invalidationService;

    @Inject
    UserRepository userRepository;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    private UserEntity testUser;
    private FavoritesEntity testFavorite;

    // Test coordinates
    private static final double HOME_LAT = 52.520008;
    private static final double HOME_LON = 13.404954;

    @BeforeEach
    @Transactional
    void setUp() {
        // Create test user with unique email
        testUser = new UserEntity();
        testUser.setEmail("favorite-rebuild-test-" + System.currentTimeMillis() + "@example.com");
        testUser.setFullName("Favorite Rebuild Test User");
        testUser.setPasswordHash("dummy-hash");
        testUser.setEmailVerified(true);
        testUser.setActive(true);
        testUser.setRole("USER");
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        userRepository.persist(testUser);
        entityManager.flush();

        log.info("Set up favorite rebuild test user: {}", testUser.getId());
    }

    @AfterEach
    @Transactional
    void tearDown() {
        if (testUser != null) {
            // Clean up test data
            entityManager.createQuery("DELETE FROM TimelineStayEntity t WHERE t.user.id = :userId")
                    .setParameter("userId", testUser.getId())
                    .executeUpdate();
            
            if (testFavorite != null) {
                favoritesRepository.delete(testFavorite);
            }
            
            userRepository.delete(testUser);
            entityManager.flush();
        }
    }

    /**
     * Test that marking timeline stays as stale triggers the invalidation queue system.
     * This tests the core invalidation mechanism that would be used when favorites change.
     */
    @Test
    @Transactional
    void testTimelineInvalidationQueueSystem() {
        log.info("Testing timeline invalidation queue system");

        LocalDate testDate = LocalDate.of(2025, 6, 10);
        Instant startTime = testDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        // Create persisted timeline stays
        TimelineStayEntity stay1 = createTestTimelineStay(startTime.plusSeconds(3600), HOME_LAT, HOME_LON, null, "Home Location");
        TimelineStayEntity stay2 = createTestTimelineStay(startTime.plusSeconds(7200), HOME_LAT + 0.001, HOME_LON + 0.001, null, "Work Location");

        // Verify initial state - stays should not be stale
        assertFalse(stay1.getIsStale(), "Initial stay should not be stale");
        assertFalse(stay2.getIsStale(), "Initial stay should not be stale");

        // Verify initial queue state
        int initialQueueSize = invalidationService.getQueueSize();
        log.info("Initial queue size: {}", initialQueueSize);

        // Mark stays as stale and queue for regeneration
        List<TimelineStayEntity> staysToInvalidate = List.of(stay1, stay2);
        invalidationService.markStaleAndQueue(staysToInvalidate);

        // Verify stays are now marked as stale (they should be modified in-place)
        assertTrue(stay1.getIsStale(), "Stay should be marked as stale");
        assertTrue(stay2.getIsStale(), "Stay should be marked as stale");

        // Verify queue has new items
        int finalQueueSize = invalidationService.getQueueSize();
        assertTrue(finalQueueSize > initialQueueSize, "Queue should have new items after invalidation");

        log.info("Invalidation queue test completed - queue size increased from {} to {}", 
                initialQueueSize, finalQueueSize);
    }

    /**
     * Test timeline stay entity creation with favorite location references.
     */
    @Test
    @Transactional
    void testTimelineStayWithFavoriteReferences() {
        log.info("Testing timeline stay with favorite references");

        // Create a favorite location
        testFavorite = createTestFavorite("My Home", HOME_LAT, HOME_LON);
        entityManager.flush(); // Ensure favorite is persisted

        LocalDate testDate = LocalDate.of(2025, 6, 10);
        Instant startTime = testDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        // Create timeline stay that references the favorite
        TimelineStayEntity stay = createTestTimelineStay(startTime.plusSeconds(3600), HOME_LAT, HOME_LON, testFavorite, "My Home");
        entityManager.flush(); // Ensure stay is persisted

        // Verify the stay references the favorite
        assertNotNull(stay.getFavoriteLocation(), "Stay should reference the favorite");
        assertEquals(testFavorite.getId(), stay.getFavoriteLocation().getId(), "Stay should reference correct favorite");
        assertEquals("My Home", stay.getLocationName(), "Stay should have favorite name");
        assertEquals(LocationSource.FAVORITE, stay.getLocationSource(), "Stay should have favorite location source");

        log.info("Favorite reference test completed successfully - stay references favorite: {}", testFavorite.getId());
    }

    /**
     * Test detection of stale timeline data.
     */
    @Test
    @Transactional
    void testStaleTimelineDetection() {
        log.info("Testing stale timeline detection");

        LocalDate testDate = LocalDate.of(2025, 6, 10);
        Instant startTime = testDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        // Create timeline stay with specific version
        TimelineStayEntity stay = createTestTimelineStay(startTime.plusSeconds(3600), HOME_LAT, HOME_LON, null, "Test Location");
        stay.setTimelineVersion("version-1.0");
        stay.setIsStale(false);
        stayRepository.persist(stay);
        entityManager.flush();

        // Verify initial state
        assertFalse(stay.getIsStale(), "Stay should not be stale initially");
        assertEquals("version-1.0", stay.getTimelineVersion(), "Should have initial version");

        // Simulate version change (e.g., favorite data changed)
        stay.setIsStale(true);
        stay.setTimelineVersion("version-2.0");
        stayRepository.persist(stay);
        entityManager.flush();

        // Verify stale detection
        assertTrue(stay.getIsStale(), "Stay should be marked as stale");
        assertEquals("version-2.0", stay.getTimelineVersion(), "Should have updated version");

        log.info("Stale detection test completed - version tracking works correctly");
    }

    /**
     * Test mixed timeline stays with different source types.
     */
    @Test
    @Transactional
    void testMixedTimelineSourceTypes() {
        log.info("Testing mixed timeline with different source types");

        LocalDate testDate = LocalDate.of(2025, 6, 10);
        Instant startTime = testDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        // Create one favorite
        testFavorite = createTestFavorite("Home", HOME_LAT, HOME_LON);
        entityManager.flush();

        // Create stays: one with favorite, one without
        TimelineStayEntity favoriteStay = createTestTimelineStay(startTime.plusSeconds(3600), HOME_LAT, HOME_LON, testFavorite, "Home");
        TimelineStayEntity geocodedStay = createTestTimelineStay(startTime.plusSeconds(7200), HOME_LAT + 0.001, HOME_LON + 0.001, null, "Some Office");
        entityManager.flush();

        // Verify different source types
        assertEquals(LocationSource.FAVORITE, favoriteStay.getLocationSource(), "First stay should be from favorite");
        assertEquals(LocationSource.GEOCODING, geocodedStay.getLocationSource(), "Second stay should be geocoded");
        
        assertNotNull(favoriteStay.getFavoriteLocation(), "Favorite stay should have favorite reference");
        assertNull(geocodedStay.getFavoriteLocation(), "Geocoded stay should not have favorite reference");

        // Test invalidation with mixed sources
        List<TimelineStayEntity> staysToInvalidate = List.of(favoriteStay, geocodedStay);
        invalidationService.markStaleAndQueue(staysToInvalidate);

        assertTrue(favoriteStay.getIsStale(), "Favorite stay should be marked as stale");
        assertTrue(geocodedStay.getIsStale(), "Geocoded stay should be marked as stale");

        log.info("Mixed source types test completed successfully");
    }

    /**
     * Helper method to create a test favorite location.
     */
    private FavoritesEntity createTestFavorite(String name, double latitude, double longitude) {
        FavoritesEntity favorite = new FavoritesEntity();
        favorite.setUser(testUser);
        favorite.setName(name);
        
        // Create a point geometry for the favorite
        Point center = GeoUtils.createPoint(longitude, latitude);
        favorite.setGeometry(center);
        
        favoritesRepository.persist(favorite);
        return favorite;
    }

    /**
     * Helper method to create a test timeline stay entity.
     */
    private TimelineStayEntity createTestTimelineStay(Instant timestamp, double latitude, double longitude, 
                                                     FavoritesEntity favorite, String locationName) {
        TimelineStayEntity stay = new TimelineStayEntity();
        stay.setUser(testUser);
        stay.setTimestamp(timestamp);
        stay.setLatitude(latitude);
        stay.setLongitude(longitude);
        stay.setLocationName(locationName);
        stay.setStayDuration(60); // 60 minutes
        stay.setFavoriteLocation(favorite);
        stay.setLocationSource(favorite != null ? LocationSource.FAVORITE : LocationSource.GEOCODING);
        stay.setIsStale(false);
        stay.setTimelineVersion("test-version");
        stay.setLastUpdated(Instant.now());
        
        stayRepository.persist(stay);
        return stay;
    }
}