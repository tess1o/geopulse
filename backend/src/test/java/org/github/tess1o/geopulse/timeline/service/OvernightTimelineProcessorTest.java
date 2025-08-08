package org.github.tess1o.geopulse.timeline.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test the overnight timeline processing functionality.
 * Verifies the 4-step algorithm for handling overnight stays.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class OvernightTimelineProcessorTest {

    @Inject
    OvernightTimelineProcessor overnightTimelineProcessor;

    @Inject
    DailyTimelineProcessingService dailyTimelineProcessingService;

    @Inject
    UserRepository userRepository;

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineStayRepository timelineStayRepository;

    @Inject
    TimelineTripRepository timelineTripRepository;

    private UserEntity testUser;
    
    // Test coordinates
    private static final double HOME_LAT = 49.54710291;
    private static final double HOME_LON = 25.59581771;
    
    private static final double OFFICE_LAT = 49.562315391409335;
    private static final double OFFICE_LON = 25.589428119942706;

    private static final double CAFE_LAT = 49.558123;
    private static final double CAFE_LON = 25.593456;

    @BeforeEach
    @Transactional
    void setUp() {
        cleanupTestData();
        testUser = createTestUser("overnight-test@geopulse.app", "Overnight Processing Test User");
    }

    @AfterEach
    @Transactional
    void tearDown() {
        cleanupTestData();
    }

    @Test
    @Transactional
    void testOvernightStayProcessing_StayExtendedFromPreviousDay() {
        // Scenario: User at home Aug 3rd 20:00-23:59, then moves to office Aug 4th 14:00
        // Expected: Aug 3rd stay extended to Aug 4th 14:00, office stay starts at 14:00
        
        LocalDate aug3 = LocalDate.of(2025, 8, 3);
        LocalDate aug4 = LocalDate.of(2025, 8, 4);
        
        // Aug 3rd - user at home from 20:00-23:59 (4 hour stay originally)
        createGpsPoint(testUser, aug3.atTime(20, 0).toInstant(ZoneOffset.UTC), HOME_LAT, HOME_LON);
        createGpsPoint(testUser, aug3.atTime(21, 0).toInstant(ZoneOffset.UTC), HOME_LAT, HOME_LON);
        createGpsPoint(testUser, aug3.atTime(22, 0).toInstant(ZoneOffset.UTC), HOME_LAT, HOME_LON);
        createGpsPoint(testUser, aug3.atTime(23, 0).toInstant(ZoneOffset.UTC), HOME_LAT, HOME_LON);
        
        // Process Aug 3rd to create cached timeline data
        Instant aug3Start = aug3.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant aug3End = aug4.atStartOfDay(ZoneOffset.UTC).toInstant();
        
        boolean aug3Processed = dailyTimelineProcessingService.processUserTimeline(
            testUser.getId(), aug3Start, aug3End, aug3);
        
        assertTrue(aug3Processed, "Aug 3rd should be processed successfully");
        
        // Verify Aug 3rd has a cached stay
        long aug3StaysCount = timelineStayRepository.count("user.id = ?1 and timestamp >= ?2 and timestamp < ?3", 
                testUser.getId(), aug3Start, aug3End);
        assertTrue(aug3StaysCount > 0, "Aug 3rd should have cached stays");
        
        // Aug 4th - user moves to office at 14:00 and stays there
        createGpsPoint(testUser, aug4.atTime(14, 0).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        createGpsPoint(testUser, aug4.atTime(14, 30).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        createGpsPoint(testUser, aug4.atTime(15, 0).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        createGpsPoint(testUser, aug4.atTime(15, 30).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        createGpsPoint(testUser, aug4.atTime(16, 0).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        
        log.info("Created GPS data: Aug 3rd (20:00-23:00 at home) + Aug 4th (14:00-16:00 at office)");
        
        // Process Aug 4th using overnight processing
        MovementTimelineDTO aug4Timeline = overnightTimelineProcessor.processOvernightTimeline(testUser.getId(), aug4);
        
        assertNotNull(aug4Timeline, "Aug 4th timeline should be generated");
        assertTrue(aug4Timeline.getStaysCount() >= 1, "Aug 4th timeline should have at least 1 stay (office)");
        
        log.info("Aug 4th timeline: {} stays, {} trips", aug4Timeline.getStaysCount(), aug4Timeline.getTripsCount());
        
        // Verify that Aug 3rd stay was updated (extended to Aug 4th 14:00)
        long updatedAug3Stays = timelineStayRepository.count("user.id = ?1 and timestamp >= ?2 and timestamp < ?3", 
                testUser.getId(), aug3Start, aug3End);
        assertTrue(updatedAug3Stays > 0, "Aug 3rd should still have the updated stay");
        
        // The Aug 4th office stay should start at 14:00
        TimelineStayLocationDTO officeStay = aug4Timeline.getStays().stream()
                .filter(stay -> stay.getTimestamp().equals(aug4.atTime(14, 0).toInstant(ZoneOffset.UTC)))
                .findFirst()
                .orElse(null);
                
        assertNotNull(officeStay, "Should find office stay starting at 14:00");
        log.info("Office stay: {} at {} (duration: {} seconds)", 
                officeStay.getLocationName(), officeStay.getTimestamp(), officeStay.getStayDuration());
        
        log.info("SUCCESS: Overnight stay processing working correctly!");
    }

    @Test
    @Transactional
    void testOvernightProcessing_NoPreviousEvents() {
        // Scenario: No previous events exist, should fall back to standard 00:00-23:59 processing
        
        LocalDate aug4 = LocalDate.of(2025, 8, 4);
        
        // Aug 4th - user at office from 09:00-17:00
        createGpsPoint(testUser, aug4.atTime(9, 0).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        createGpsPoint(testUser, aug4.atTime(10, 0).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        createGpsPoint(testUser, aug4.atTime(11, 0).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        createGpsPoint(testUser, aug4.atTime(12, 0).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        createGpsPoint(testUser, aug4.atTime(13, 0).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        createGpsPoint(testUser, aug4.atTime(14, 0).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        createGpsPoint(testUser, aug4.atTime(15, 0).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        createGpsPoint(testUser, aug4.atTime(16, 0).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        createGpsPoint(testUser, aug4.atTime(17, 0).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        
        log.info("Created GPS data: Aug 4th (09:00-17:00 at office) with no previous events");
        
        // Process Aug 4th using overnight processing (should fall back to standard)
        MovementTimelineDTO aug4Timeline = overnightTimelineProcessor.processOvernightTimeline(testUser.getId(), aug4);
        
        assertNotNull(aug4Timeline, "Aug 4th timeline should be generated");
        assertTrue(aug4Timeline.getStaysCount() >= 1, "Aug 4th timeline should have at least 1 stay (office)");
        
        log.info("Aug 4th timeline (fallback): {} stays, {} trips", aug4Timeline.getStaysCount(), aug4Timeline.getTripsCount());
        
        // The office stay should start at 09:00 (standard processing)
        TimelineStayLocationDTO officeStay = aug4Timeline.getStays().get(0);
        assertEquals(aug4.atTime(9, 0).toInstant(ZoneOffset.UTC), officeStay.getTimestamp(),
                    "Office stay should start at 09:00 (standard processing)");
        
        log.info("SUCCESS: Fallback to standard processing working correctly!");
    }

    @Test
    @Transactional
    void testOvernightProcessing_MultipleStaysInDay() {
        // Scenario: User has multiple stays on processing day after overnight stay
        // Expected: Previous stay extended, then multiple new stays saved
        
        LocalDate aug3 = LocalDate.of(2025, 8, 3);
        LocalDate aug4 = LocalDate.of(2025, 8, 4);
        
        // Aug 3rd - user at home from 22:00-23:59
        createGpsPoint(testUser, aug3.atTime(22, 0).toInstant(ZoneOffset.UTC), HOME_LAT, HOME_LON);
        createGpsPoint(testUser, aug3.atTime(23, 0).toInstant(ZoneOffset.UTC), HOME_LAT, HOME_LON);
        
        // Process Aug 3rd
        Instant aug3Start = aug3.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant aug3End = aug4.atStartOfDay(ZoneOffset.UTC).toInstant();
        
        boolean aug3Processed = dailyTimelineProcessingService.processUserTimeline(
            testUser.getId(), aug3Start, aug3End, aug3);
        
        assertTrue(aug3Processed, "Aug 3rd should be processed successfully");
        
        // Aug 4th - user goes to office (10:00-14:00), then cafe (15:00-18:00)
        createGpsPoint(testUser, aug4.atTime(10, 0).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        createGpsPoint(testUser, aug4.atTime(11, 0).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        createGpsPoint(testUser, aug4.atTime(12, 0).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        createGpsPoint(testUser, aug4.atTime(13, 0).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        createGpsPoint(testUser, aug4.atTime(14, 0).toInstant(ZoneOffset.UTC), OFFICE_LAT, OFFICE_LON);
        
        createGpsPoint(testUser, aug4.atTime(15, 0).toInstant(ZoneOffset.UTC), CAFE_LAT, CAFE_LON);
        createGpsPoint(testUser, aug4.atTime(16, 0).toInstant(ZoneOffset.UTC), CAFE_LAT, CAFE_LON);
        createGpsPoint(testUser, aug4.atTime(17, 0).toInstant(ZoneOffset.UTC), CAFE_LAT, CAFE_LON);
        createGpsPoint(testUser, aug4.atTime(18, 0).toInstant(ZoneOffset.UTC), CAFE_LAT, CAFE_LON);
        
        log.info("Created GPS data: Aug 3rd (22:00-23:00 at home) + Aug 4th (10:00-14:00 office, 15:00-18:00 cafe)");
        
        // Process Aug 4th using overnight processing
        MovementTimelineDTO aug4Timeline = overnightTimelineProcessor.processOvernightTimeline(testUser.getId(), aug4);
        
        assertNotNull(aug4Timeline, "Aug 4th timeline should be generated");
        assertTrue(aug4Timeline.getStaysCount() >= 2, "Aug 4th timeline should have at least 2 stays (office + cafe)");
        
        log.info("Aug 4th timeline: {} stays, {} trips", aug4Timeline.getStaysCount(), aug4Timeline.getTripsCount());
        
        // Verify we have office and cafe stays
        boolean hasOfficeStay = aug4Timeline.getStays().stream()
                .anyMatch(stay -> stay.getTimestamp().equals(aug4.atTime(10, 0).toInstant(ZoneOffset.UTC)));
        boolean hasCafeStay = aug4Timeline.getStays().stream()
                .anyMatch(stay -> stay.getTimestamp().equals(aug4.atTime(15, 0).toInstant(ZoneOffset.UTC)));
                
        assertTrue(hasOfficeStay, "Should have office stay starting at 10:00");
        assertTrue(hasCafeStay, "Should have cafe stay starting at 15:00");
        
        log.info("SUCCESS: Multiple stays processing working correctly!");
    }

    @Transactional
    void cleanupTestData() {
        timelineTripRepository.delete("user.email like ?1", "%@geopulse.app");
        timelineStayRepository.delete("user.email like ?1", "%@geopulse.app");
        gpsPointRepository.delete("user.email like ?1", "%@geopulse.app");
        userRepository.delete("email like ?1", "%@geopulse.app");
    }

    private UserEntity createTestUser(String email, String fullName) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPasswordHash("test-hash");
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        userRepository.persist(user);
        return user;
    }

    private void createGpsPoint(UserEntity user, Instant timestamp, double latitude, double longitude) {
        GpsPointEntity gpsPoint = new GpsPointEntity();
        gpsPoint.setUser(user);
        gpsPoint.setTimestamp(timestamp);
        gpsPoint.setCoordinates(GeoUtils.createPoint(longitude, latitude));
        gpsPoint.setAccuracy(5.0);
        gpsPoint.setAltitude(100.0);
        gpsPoint.setVelocity(0.0);
        gpsPoint.setBattery(85.0);
        gpsPoint.setDeviceId("test-device");
        gpsPoint.setSourceType(GpsSourceType.OWNTRACKS);
        gpsPoint.setCreatedAt(Instant.now());
        gpsPointRepository.persist(gpsPoint);
    }
}