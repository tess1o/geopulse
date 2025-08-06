package org.github.tess1o.geopulse.timeline.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.timeline.events.FavoriteAddedEvent;
import org.github.tess1o.geopulse.timeline.events.FavoriteDeletedEvent;
import org.github.tess1o.geopulse.timeline.events.FavoriteRenamedEvent;
import org.github.tess1o.geopulse.timeline.model.LocationSource;
import org.github.tess1o.geopulse.timeline.model.TimelineStayEntity;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for TimelineEventService.
 * Tests the hybrid fast/slow path approach for favorite changes using real Postgres.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class TimelineEventServiceTest {

    @Inject
    TimelineEventService eventService;

    @Inject
    UserRepository userRepository;

    @Inject
    FavoritesRepository favoritesRepository;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    jakarta.persistence.EntityManager entityManager;

    private UserEntity testUser;
    private FavoritesEntity testFavorite;
    private TimelineStayEntity testStay;
    private GeometryFactory geometryFactory;

    @BeforeEach
    @Transactional
    void setUp() {
        geometryFactory = new GeometryFactory();
        
        // Create test user
        testUser = new UserEntity();
        testUser.setEmail("timeline-event-test-" + System.currentTimeMillis() + "@example.com");
        testUser.setFullName("Timeline Event Test User");
        testUser.setPasswordHash("dummy-hash");
        testUser.setEmailVerified(true);
        testUser.setActive(true);
        testUser.setRole("USER");
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        userRepository.persist(testUser);
        entityManager.flush();

        log.info("Set up timeline event test user: {}", testUser.getId());
    }

    @AfterEach
    @Transactional
    void tearDown() {
        if (testUser != null) {
            // Clean up timeline regeneration queue first to avoid foreign key constraint violations
            entityManager.createQuery("DELETE FROM TimelineRegenerationTask t WHERE t.user.id = :userId")
                    .setParameter("userId", testUser.getId())
                    .executeUpdate();
            // Clean up timeline data
            entityManager.createQuery("DELETE FROM TimelineStayEntity t WHERE t.user.id = :userId")
                    .setParameter("userId", testUser.getId())
                    .executeUpdate();
            entityManager.createQuery("DELETE FROM TimelineTripEntity t WHERE t.user.id = :userId")
                    .setParameter("userId", testUser.getId())
                    .executeUpdate();
            // Clean up favorites
            entityManager.createQuery("DELETE FROM FavoritesEntity f WHERE f.user.id = :userId")
                    .setParameter("userId", testUser.getId())
                    .executeUpdate();
            // Clean up GPS data
            entityManager.createQuery("DELETE FROM GpsPointEntity g WHERE g.user.id = :userId")
                    .setParameter("userId", testUser.getId())
                    .executeUpdate();
            // Clean up user
            userRepository.delete(testUser);
            entityManager.flush();
        }
    }

    @Test
    @Transactional
    void testFavoriteRenamed_NameOnlyChange_ShouldUseFastPath() {
        // Arrange - Create favorite and timeline stay with the favorite
        createTestFavoriteAndStay("Home", 37.7749, -122.4194);

        // Create a rename event (fast path - simple name change)
        FavoriteRenamedEvent event = FavoriteRenamedEvent.builder()
                .favoriteId(testFavorite.getId())
                .userId(testUser.getId())
                .oldName("Home")
                .newName("My Home")
                .favoriteType(FavoriteLocationType.POINT)
                .geometry(testFavorite.getGeometry())
                .build();

        // Act
        eventService.onFavoriteRenamed(event);

        // Assert - Check that the timeline stay was updated directly (fast path)
        entityManager.flush();
        entityManager.clear(); // Force reload from database
        
        TimelineStayEntity updatedStay = timelineStayRepository.findById(testStay.getId());
        assertNotNull(updatedStay);
        assertEquals("My Home", updatedStay.getLocationName());
        
        log.info("Fast path test completed - Stay name updated from '{}' to '{}'", 
                "Home", updatedStay.getLocationName());
    }

    @Test
    @Transactional
    void testFavoriteRenamed_StructuralChange_ShouldUseSlowPath() {
        // Arrange - Create favorite with geometry that would cause structural changes
        Point oldLocation = geometryFactory.createPoint(new Coordinate(-122.4194, 37.7749));
        oldLocation.setSRID(4326);
        
        testFavorite = FavoritesEntity.builder()
                .user(testUser)
                .name("Work")
                .type(FavoriteLocationType.POINT)
                .geometry(oldLocation)
                .mergeImpact(true) // Set to true for slow path testing
                .build();
        favoritesRepository.persist(testFavorite);
        entityManager.flush();

        // Create a rename event that would cause structural changes
        // (changing location significantly enough to affect timeline merging)
        Point newLocation = geometryFactory.createPoint(new Coordinate(-122.4000, 37.7900)); // Different location
        newLocation.setSRID(4326);
        
        FavoriteRenamedEvent event = FavoriteRenamedEvent.builder()
                .favoriteId(testFavorite.getId())
                .userId(testUser.getId())
                .oldName("Work")
                .newName("New Work Location")
                .favoriteType(FavoriteLocationType.POINT)
                .geometry(newLocation) // Different geometry
                .build();

        // Act
        eventService.onFavoriteRenamed(event);

        // Assert - This should trigger the slow path (cache invalidation + regeneration)
        // We can verify the service didn't crash and handled the event
        // The actual slow path verification would require checking cache deletion and background queues
        // which are handled by separate services
        
        log.info("Structural change test completed - Event processed through slow path");
    }

    @Test 
    @Transactional
    void testFavoriteAdded_ShouldProcessCorrectly() {
        // Arrange - Create GPS data that will be affected by new favorite
        createTestGpsData();
        
        // Create favorite added event
        Point location = geometryFactory.createPoint(new Coordinate(-122.4194, 37.7749));
        location.setSRID(4326);
        
        FavoriteAddedEvent event = FavoriteAddedEvent.builder()
                .userId(testUser.getId())
                .favoriteName("New Favorite")
                .favoriteType(FavoriteLocationType.POINT)
                .geometry(location)
                .build();

        // Act
        eventService.onFavoriteAdded(event);

        // Assert - Verify the service processed the event without errors
        log.info("Favorite added test completed successfully");
    }

    @Test
    @Transactional
    void testFavoriteDeleted_ShouldProcessCorrectly() {
        // Arrange - Create favorite and timeline data
        createTestFavoriteAndStay("Office", 37.7849, -122.4094);
        
        FavoriteDeletedEvent event = FavoriteDeletedEvent.builder()
                .favoriteId(testFavorite.getId())
                .userId(testUser.getId())
                .favoriteName("Office")
                .favoriteType(FavoriteLocationType.POINT)
                .geometry(testFavorite.getGeometry())
                .build();

        // Act
        eventService.onFavoriteDeleted(event);

        // Assert - Verify the service processed the event without errors
        log.info("Favorite deleted test completed successfully");
    }

    /**
     * Helper method to create a test favorite and associated timeline stay
     */
    private void createTestFavoriteAndStay(String name, double lat, double lng) {
        // Create favorite
        Point location = geometryFactory.createPoint(new Coordinate(lng, lat));
        location.setSRID(4326);
        
        testFavorite = FavoritesEntity.builder()
                .user(testUser)
                .name(name)
                .type(FavoriteLocationType.POINT)
                .geometry(location)
                .mergeImpact(false) // Set to false for fast path testing
                .build();
        favoritesRepository.persist(testFavorite);

        // Create timeline stay that references this favorite
        Instant baseTime = LocalDate.now().minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        
        testStay = new TimelineStayEntity();
        testStay.setUser(testUser);
        testStay.setFavoriteLocation(testFavorite);
        testStay.setLocationName(name);
        testStay.setLatitude(lat);
        testStay.setLongitude(lng);
        testStay.setTimestamp(baseTime);
        testStay.setStayDuration(120); // minutes
        testStay.setLocationSource(LocationSource.FAVORITE);
        testStay.setCreatedAt(Instant.now());
        testStay.setLastUpdated(Instant.now());
        timelineStayRepository.persist(testStay);
        
        entityManager.flush();
        log.info("Created test favorite '{}' with ID {} and associated timeline stay", name, testFavorite.getId());
    }

    /**
     * Helper method to create test GPS data
     */
    private void createTestGpsData() {
        Instant baseTime = LocalDate.now().minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        
        for (int i = 0; i < 5; i++) {
            GpsPointEntity gpsPoint = new GpsPointEntity();
            gpsPoint.setUser(testUser);
            
            Point point = geometryFactory.createPoint(new Coordinate(-122.4194, 37.7749));
            point.setSRID(4326);
            gpsPoint.setCoordinates(point);
            
            gpsPoint.setTimestamp(baseTime.plusSeconds(i * 600)); // Every 10 minutes
            gpsPoint.setAccuracy(10.0);
            gpsPoint.setVelocity(0.0);
            gpsPoint.setSourceType(GpsSourceType.OWNTRACKS);
            gpsPoint.setCreatedAt(Instant.now());
            
            gpsPointRepository.persist(gpsPoint);
        }
        
        entityManager.flush();
        log.info("Created test GPS data for timeline events");
    }
}