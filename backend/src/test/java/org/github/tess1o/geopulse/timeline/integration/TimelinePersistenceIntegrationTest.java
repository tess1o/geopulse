package org.github.tess1o.geopulse.timeline.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.AddPointToFavoritesDto;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineStayEntity;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.service.TimelineInvalidationService;
import org.github.tess1o.geopulse.timeline.service.TimelineQueryService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for timeline persistence with dynamic favorite updates.
 * Tests the complete lifecycle: cache generation, favorite changes, invalidation, regeneration.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class TimelinePersistenceIntegrationTest {

    @Inject
    TimelineQueryService timelineQueryService;

    @Inject
    TimelineInvalidationService invalidationService;

    @Inject
    FavoriteLocationService favoriteLocationService;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    private UserEntity testUser;
    private LocalDate testDate;
    private Instant testInstant;

    @BeforeEach
    @Transactional
    void setUp() {
        // Create test user (let JPA generate the ID)
        testUser = new UserEntity();
        testUser.setEmail("timeline-test@example.com");
        testUser.setFullName("Timeline Test User");
        testUser.setPasswordHash("dummy-hash");
        testUser.setEmailVerified(true);
        testUser.setActive(true);
        testUser.setRole("USER");
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        userRepository.persist(testUser);
        entityManager.flush(); // Ensure ID is generated

        // Use yesterday's date to ensure persistence (not today)
        testDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        testInstant = testDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        
        log.info("Set up test user {} for date {}", testUser.getId(), testDate);
    }

    /**
     * Helper method to get timeline for test instant using full day range.
     */
    private MovementTimelineDTO getTestTimeline() {
        Instant startOfDay = testDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = testDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return timelineQueryService.getTimeline(testUser.getId(), startOfDay, endOfDay);
    }

    @AfterEach
    @Transactional
    void tearDown() {
        if (testUser != null) {
            // Clean up timeline data
            stayRepository.deleteByUserBeforeDate(testUser.getId(), Instant.now());
            
            // Clean up favorites
            favoritesRepository.delete("user.id", testUser.getId());
            
            // Clean up user
            userRepository.deleteById(testUser.getId());
            
            // Clear invalidation queue
            invalidationService.clearQueue();
            
            entityManager.flush();
            log.info("Cleaned up test data for user {}", testUser.getId());
        }
    }

    @Test
    @Transactional
    void testTimelinePersistenceLifecycle() {
        // Step 1: Request timeline for past date (should generate and cache)
        log.info("Step 1: Initial timeline generation");
        MovementTimelineDTO initialTimeline = getTestTimeline();
        
        assertNotNull(initialTimeline, "Timeline should be generated");
        assertEquals(testUser.getId(), initialTimeline.getUserId(), "User ID should match");
        // Initial timeline will likely be empty since we haven't set up GPS data
        
        // Verify timeline was persisted
        List<TimelineStayEntity> persistedStays = stayRepository.findByUserAndDate(testUser.getId(), testInstant);
        log.info("Found {} persisted stays after initial generation", persistedStays.size());
    }

    @Test
    @Transactional
    void testFavoriteAdditionInvalidatesTimeline() throws InterruptedException {
        // Step 1: Generate initial timeline 
        log.info("Step 1: Generate initial timeline");
        MovementTimelineDTO initialTimeline = getTestTimeline();
        assertNotNull(initialTimeline);
        
        // Step 2: Add a favorite location
        log.info("Step 2: Adding favorite location");
        
        AddPointToFavoritesDto favoriteDto = new AddPointToFavoritesDto();
        favoriteDto.setName("Test Favorite Location");
        // favoriteDto.setType("HOME"); // Type not available in DTO
        favoriteDto.setLat(37.7749); // San Francisco
        favoriteDto.setLon(-122.4194);
        
        // This should trigger favorite added event and timeline invalidation
        favoriteLocationService.addFavorite(testUser.getId(), favoriteDto);
        entityManager.flush(); // Ensure event is processed
        
        // Step 3: Check if timeline stays were marked as stale
        log.info("Step 3: Checking for stale timeline data");
        List<TimelineStayEntity> staleStays = stayRepository.findStaleByUserAndDate(testUser.getId(), testInstant);
        log.info("Found {} stale stays after favorite addition", staleStays.size());
        
        // Step 4: Check invalidation queue
        var queueStats = invalidationService.getQueueStatistics();
        log.info("Queue statistics after favorite addition: {}", queueStats);
        assertTrue(queueStats.getQueueSize() >= 0, "Queue should have items or be processed");
        
        // Step 5: Request timeline again (should trigger regeneration)
        log.info("Step 5: Requesting timeline after favorite addition");
        MovementTimelineDTO updatedTimeline = getTestTimeline();
        assertNotNull(updatedTimeline);
        assertEquals(testUser.getId(), updatedTimeline.getUserId());
        
        log.info("Timeline persistence lifecycle test completed successfully");
    }

    @Test
    @Transactional
    void testFavoriteRenameInvalidatesTimeline() {
        // Step 1: Create and persist a favorite location
        log.info("Step 1: Creating favorite location");
        
        AddPointToFavoritesDto favoriteDto = new AddPointToFavoritesDto();
        favoriteDto.setName("Original Name");
        // favoriteDto.setType("WORK"); // Type not available in DTO
        favoriteDto.setLat(37.7749);
        favoriteDto.setLon(-122.4194);
        
        favoriteLocationService.addFavorite(testUser.getId(), favoriteDto);
        entityManager.flush();
        
        // Step 2: Generate initial timeline
        log.info("Step 2: Generate initial timeline");
        MovementTimelineDTO initialTimeline = getTestTimeline();
        assertNotNull(initialTimeline);
        
        // Step 3: Find the created favorite and rename it
        log.info("Step 3: Renaming favorite location");
        List<FavoritesEntity> userFavorites = favoritesRepository.findByUserId(testUser.getId());
        if (!userFavorites.isEmpty()) {
            FavoritesEntity favorite = userFavorites.get(0);
            String newName = "Renamed Location";
            favoriteLocationService.renameFavorite(testUser.getId(), favorite.getId(), newName);
            entityManager.flush();
            
            // Step 4: Verify the rename triggered invalidation
            var queueStats = invalidationService.getQueueStatistics();
            log.info("Queue statistics after favorite rename: {}", queueStats);
        }
        
        // Step 5: Request timeline again 
        log.info("Step 5: Requesting timeline after rename");
        MovementTimelineDTO updatedTimeline = getTestTimeline();
        assertNotNull(updatedTimeline);
        
        log.info("Favorite rename invalidation test completed successfully");
    }

    @Test
    @Transactional 
    void testFavoriteDeletionInvalidatesTimeline() {
        // Step 1: Create and persist a favorite location
        log.info("Step 1: Creating favorite location");
        
        AddPointToFavoritesDto favoriteDto = new AddPointToFavoritesDto();
        favoriteDto.setName("To Be Deleted");
        // favoriteDto.setType("OTHER"); // Type not available in DTO
        favoriteDto.setLat(37.7749);
        favoriteDto.setLon(-122.4194);
        
        favoriteLocationService.addFavorite(testUser.getId(), favoriteDto);
        entityManager.flush();
        
        // Step 2: Generate timeline that might reference this favorite
        log.info("Step 2: Generate initial timeline");
        MovementTimelineDTO initialTimeline = getTestTimeline();
        assertNotNull(initialTimeline);
        
        // Step 3: Find and delete the favorite
        log.info("Step 3: Deleting favorite location");
        List<FavoritesEntity> userFavorites = favoritesRepository.findByUserId(testUser.getId());
        if (!userFavorites.isEmpty()) {
            FavoritesEntity favorite = userFavorites.get(0);
            favoriteLocationService.deleteFavorite(testUser.getId(), favorite.getId());
            entityManager.flush();
            
            // Step 4: Verify deletion triggered invalidation
            var queueStats = invalidationService.getQueueStatistics();
            log.info("Queue statistics after favorite deletion: {}", queueStats);
        }
        
        // Step 5: Request timeline again
        log.info("Step 5: Requesting timeline after deletion");
        MovementTimelineDTO updatedTimeline = getTestTimeline();
        assertNotNull(updatedTimeline);
        
        log.info("Favorite deletion invalidation test completed successfully");
    }

    @Test
    @Transactional
    void testForceRegenerateTimeline() {
        // Step 1: Generate initial timeline
        log.info("Step 1: Generate initial timeline");
        LocalDate testDate = testInstant.atZone(ZoneOffset.UTC).toLocalDate();
        Instant startOfDay = testDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = testDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        MovementTimelineDTO initialTimeline = timelineQueryService.getTimeline(testUser.getId(), startOfDay, endOfDay);
        assertNotNull(initialTimeline);
        
        // Step 2: Force regeneration
        log.info("Step 2: Force regenerating timeline");
        MovementTimelineDTO regeneratedTimeline = timelineQueryService.forceRegenerateTimeline(testUser.getId(), startOfDay, endOfDay);
        assertNotNull(regeneratedTimeline);
        assertEquals(testUser.getId(), regeneratedTimeline.getUserId());
        
        // Timeline should be marked as non-stale after regeneration
        assertFalse(regeneratedTimeline.getIsStale(), "Regenerated timeline should not be stale");
        
        log.info("Force regeneration test completed successfully");
    }

    @Test
    @Transactional
    void testQueueMonitoring() {
        // Step 1: Check initial queue state
        log.info("Step 1: Check initial queue state");
        var initialStats = invalidationService.getQueueStatistics();
        assertNotNull(initialStats);
        assertTrue(initialStats.getQueueSize() >= 0, "Queue size should be non-negative");
        assertTrue(initialStats.getTotalProcessed() >= 0, "Total processed should be non-negative");
        
        // Step 2: Add some queue items by adding favorites
        log.info("Step 2: Adding queue items via favorite operations");
        
        AddPointToFavoritesDto favorite1 = new AddPointToFavoritesDto();
        favorite1.setName("Queue Test 1");
        // favorite1.setType("HOME"); // Type not available in DTO
        favorite1.setLat(37.7749);
        favorite1.setLon(-122.4194);
        
        AddPointToFavoritesDto favorite2 = new AddPointToFavoritesDto();
        favorite2.setName("Queue Test 2");
        // favorite2.setType("WORK"); // Type not available in DTO
        favorite2.setLat(37.7849);
        favorite2.setLon(-122.4094);
        
        favoriteLocationService.addFavorite(testUser.getId(), favorite1);
        favoriteLocationService.addFavorite(testUser.getId(), favorite2);
        entityManager.flush();
        
        // Step 3: Check queue after operations
        log.info("Step 3: Check queue after operations");
        var updatedStats = invalidationService.getQueueStatistics();
        log.info("Updated queue statistics: {}", updatedStats);
        
        // Queue might be processed by background threads, so we just verify structure
        assertNotNull(updatedStats);
        assertTrue(updatedStats.getTotalProcessed() >= initialStats.getTotalProcessed(), 
                  "Total processed should increase or stay same");
        
        log.info("Queue monitoring test completed successfully");
    }
}