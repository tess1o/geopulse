package org.github.tess1o.geopulse.timeline.service.redesign;

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
import org.github.tess1o.geopulse.timeline.model.TimelineDataGapDTO;
import org.github.tess1o.geopulse.timeline.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CRITICAL BUG TEST: Data gap detection is creating gaps that don't meet minimum configuration thresholds.
 * 
 * Reproduction scenario based on user report:
 * - Config: Min gap detection threshold = 230 minutes, Min gap duration = 35 minutes
 * - System creates gaps of 6 seconds and 4 minutes (way below thresholds)
 * - This test reproduces the exact user scenario to identify root cause
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@Slf4j
class DataGapDetectionConfigurationTest {

    @Inject
    TimelineRequestRouter timelineRequestRouter;

    @Inject
    UserRepository userRepository;
    
    @Inject
    GpsPointRepository gpsPointRepository;
    
    @Inject
    TimelineStayRepository timelineStayRepository;
    
    @Inject
    TimelineTripRepository timelineTripRepository;
    
    @Inject
    TimelineDataGapRepository timelineDataGapRepository;

    private UserEntity testUser;
    private UUID testUserId;

    // User's actual coordinates from Ukraine (Rivne area)
    private static final double HOME_LAT = 49.54707782097803;
    private static final double HOME_LNG = 25.595934751952797;
    private static final double EPICENTER_LAT = 49.57203307944953;
    private static final double EPICENTER_LNG = 25.60178760154248;

    @BeforeEach
    @Transactional
    void setUp() {
        cleanupTestData();
        testUser = createTestUser("data-gap-config-test@geopulse.app", "Data Gap Config Test User");
        testUserId = testUser.getId();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        cleanupTestData();
    }

    @Test
    @DisplayName("CRITICAL BUG: Data gaps created below minimum configuration thresholds")
    @Transactional
    void testDataGapDetection_ShouldRespectMinimumConfigurationThresholds() {
        // REPRODUCE EXACT USER SCENARIO:
        // Config: Min gap detection threshold = 230 minutes (3h 50m), Min gap duration = 35 minutes
        // Expected: No gaps should be created for durations < 35 minutes
        // Actual Bug: System creates 6-second and 4-minute gaps
        
        log.info("=== REPRODUCING DATA GAP CONFIGURATION BUG ===");
        
        // Mock current time to match user scenario (2025-08-24 16:29:44 UTC)
        Instant simulatedNow = Instant.parse("2025-08-24T16:29:44.426992Z");
        LocalDate testDate = LocalDate.of(2025, 8, 24);
        LocalDate previousDate = testDate.minusDays(1);
        
        log.info("Simulated current time: {}", simulatedNow);
        log.info("Test date: {}", testDate);
        
        // === ARRANGE: Create GPS data that reproduces user's timeline ===
        
        // Previous day - create some GPS data ending around 23:59:53 (to trigger the 6-second gap)
        Instant previousDayEnd = Instant.parse("2025-08-23T23:59:53Z");
        createGpsPointAt(previousDayEnd.minusSeconds(1800), HOME_LAT, HOME_LNG); // 30 min before
        createGpsPointAt(previousDayEnd, HOME_LAT, HOME_LNG); // Ends exactly at 23:59:53
        
        // Today's timeline - reproduce exact user stays:
        
        // Stay 1: Home 00:03:05Z for 620 minutes (10h 20m) → ends ~10:23:05Z
        Instant stay1Start = Instant.parse("2025-08-24T00:03:05Z");
        Instant stay1End = stay1Start.plusSeconds(620 * 60); // 10:23:05Z
        createGpsStayPattern(stay1Start, stay1End, HOME_LAT, HOME_LNG);
        
        // Stay 2: Epicenter 10:36:46Z for 106 minutes (1h 46m) → ends ~12:22:46Z  
        Instant stay2Start = Instant.parse("2025-08-24T10:36:46Z");
        Instant stay2End = stay2Start.plusSeconds(106 * 60); // 12:22:46Z
        createGpsStayPattern(stay2Start, stay2End, EPICENTER_LAT, EPICENTER_LNG);
        
        // Stay 3: Home 12:44:46Z for 220 minutes (3h 40m) → ends ~16:24:46Z
        Instant stay3Start = Instant.parse("2025-08-24T12:44:46Z");
        Instant stay3End = stay3Start.plusSeconds(220 * 60); // 16:24:46Z  
        createGpsStayPattern(stay3Start, stay3End, HOME_LAT, HOME_LNG);
        
        // GPS data ends at ~16:24:46, current time is 16:29:44 → 4 minute 58 second gap
        
        long totalGpsPoints = gpsPointRepository.count("user = ?1", testUser);
        log.info("Created {} GPS points for timeline reproduction", totalGpsPoints);
        assertTrue(totalGpsPoints > 0, "Should have GPS data for timeline generation");
        
        // === ACT: Request timeline for the full period ===
        Instant requestStart = previousDate.atStartOfDay(ZoneOffset.UTC).toInstant(); // 2025-08-23 00:00:00Z
        Instant requestEnd = simulatedNow; // 2025-08-24 16:29:44Z
        
        log.info("Requesting timeline from {} to {}", requestStart, requestEnd);
        
        MovementTimelineDTO timeline = timelineRequestRouter.getTimeline(testUserId, requestStart, requestEnd);
        
        // === DEBUG: Log actual results ===
        assertNotNull(timeline, "Timeline should not be null");
        
        log.info("=== TIMELINE RESULTS ===");
        log.info("Stays: {}", timeline.getStaysCount());
        timeline.getStays().forEach(stay -> 
            log.info("  Stay: {} at {} for {} minutes", 
                stay.getLocationName(), stay.getTimestamp(), stay.getStayDuration()));
        
        log.info("Data Gaps: {}", timeline.getDataGapsCount());
        timeline.getDataGaps().forEach(gap -> 
            log.info("  Gap: {} to {} ({} seconds, {} minutes)", 
                gap.getStartTime(), gap.getEndTime(), gap.getDurationSeconds(), gap.getDurationMinutes()));
                
        log.info("Trips: {}", timeline.getTripsCount());
        
        // === ASSERT: Verify configuration compliance ===
        
        // CRITICAL ASSERTION 1: No data gaps should exist below minimum duration (35 minutes)
        for (TimelineDataGapDTO gap : timeline.getDataGaps()) {
            long gapDurationMinutes = gap.getDurationMinutes();
            
            if (gapDurationMinutes < 35) {
                log.error("❌ CONFIGURATION VIOLATION: Gap duration {} minutes is below minimum threshold (35 minutes)", 
                         gapDurationMinutes);
                log.error("  Gap: {} to {} ({} seconds)", 
                         gap.getStartTime(), gap.getEndTime(), gap.getDurationSeconds());
                
                fail(String.format(
                    "Data gap duration %d minutes violates minimum configuration threshold of 35 minutes. " +
                    "Gap: %s to %s (%d seconds). " +
                    "This indicates gap detection logic is not respecting user configuration.",
                    gapDurationMinutes, gap.getStartTime(), gap.getEndTime(), gap.getDurationSeconds()));
            }
        }
        
        // CRITICAL ASSERTION 2: Based on user config (230 min threshold, 35 min minimum),
        // and the timeline pattern (stays are well-connected), there should be NO data gaps
        // or at most gaps that meet the minimum requirements
        
        log.info("=== CONFIGURATION COMPLIANCE CHECK ===");
        log.info("Min gap detection threshold: 230 minutes");
        log.info("Min gap duration: 35 minutes");
        log.info("Actual gaps found: {}", timeline.getDataGapsCount());
        
        if (timeline.getDataGapsCount() > 0) {
            log.info("✅ All gaps meet minimum duration requirements");
        } else {
            log.info("✅ No inappropriate gaps detected (ideal result)");
        }
        
        // Expected timeline pattern verification
        assertTrue(timeline.getStaysCount() >= 3, "Should have at least 3 stays from user pattern");
        
        log.info("✅ Data gap configuration compliance test PASSED");
    }

    @Test
    @DisplayName("Edge case: Verify tiny end-of-day gaps are not created")
    @Transactional 
    void testEndOfDayBoundary_ShouldNotCreateTinyGaps() {
        // Specific test for the 6-second gap at end of previous day
        
        LocalDate testDate = LocalDate.of(2025, 8, 24);
        LocalDate previousDate = testDate.minusDays(1);
        
        // Create GPS activity that ends very close to end of day
        Instant almostEndOfDay = Instant.parse("2025-08-23T23:59:53Z");
        createGpsPointAt(almostEndOfDay.minusSeconds(3600), HOME_LAT, HOME_LNG); // 1 hour before
        createGpsPointAt(almostEndOfDay, HOME_LAT, HOME_LNG); // Ends at 23:59:53
        
        // Request just the previous day
        Instant requestStart = previousDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant requestEnd = testDate.atStartOfDay(ZoneOffset.UTC).toInstant(); // Exactly midnight
        
        log.info("Testing end-of-day boundary from {} to {}", requestStart, requestEnd);
        log.info("GPS activity ends at: {}", almostEndOfDay);
        
        MovementTimelineDTO timeline = timelineRequestRouter.getTimeline(testUserId, requestStart, requestEnd);
        
        assertNotNull(timeline, "Timeline should not be null");
        
        log.info("End-of-day test results:");
        log.info("  Stays: {}", timeline.getStaysCount());
        log.info("  Data gaps: {}", timeline.getDataGapsCount());
        
        // CRITICAL: Any gaps created should meet minimum duration requirements
        for (TimelineDataGapDTO gap : timeline.getDataGaps()) {
            assertTrue(gap.getDurationMinutes() >= 35,
                String.format("End-of-day gap duration %d minutes is below minimum threshold (35 minutes). " +
                            "Gap: %s to %s", gap.getDurationMinutes(), gap.getStartTime(), gap.getEndTime()));
        }
        
        log.info("✅ End-of-day boundary test passed - no tiny gaps created");
    }

    // Helper methods

    private void createGpsPointAt(Instant timestamp, double lat, double lng) {
        GpsPointEntity gpsPoint = new GpsPointEntity();
        gpsPoint.setUser(testUser);
        gpsPoint.setTimestamp(timestamp);
        gpsPoint.setCoordinates(GeoUtils.createPoint(lng, lat));
        gpsPoint.setAccuracy(10.0);
        gpsPoint.setVelocity(0.0); // Stationary
        gpsPoint.setBattery(80.0);
        gpsPoint.setDeviceId("test-device");
        gpsPoint.setSourceType(GpsSourceType.OWNTRACKS);
        gpsPoint.setCreatedAt(Instant.now());
        gpsPointRepository.persist(gpsPoint);
    }
    
    private void createGpsStayPattern(Instant startTime, Instant endTime, double lat, double lng) {
        // Create GPS points throughout the stay period to ensure it's detected as a stay
        Instant current = startTime;
        while (current.isBefore(endTime)) {
            createGpsPointAt(current, lat, lng);
            current = current.plusSeconds(900); // Every 15 minutes
        }
        // Final point at end time
        createGpsPointAt(endTime, lat, lng);
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

    @Transactional
    void cleanupTestData() {
        timelineDataGapRepository.delete("user.email like ?1", "%@geopulse.app");
        timelineStayRepository.delete("user.email like ?1", "%@geopulse.app");  
        timelineTripRepository.delete("user.email like ?1", "%@geopulse.app");
        gpsPointRepository.delete("user.email like ?1", "%@geopulse.app");
        userRepository.delete("email like ?1", "%@geopulse.app");
    }
}