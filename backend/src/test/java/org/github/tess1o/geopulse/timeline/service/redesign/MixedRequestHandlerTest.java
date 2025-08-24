package org.github.tess1o.geopulse.timeline.service.redesign;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;
import org.github.tess1o.geopulse.timeline.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for MixedRequestHandler, focusing on data gap behavior for today's timeline.
 */
@QuarkusTest
class MixedRequestHandlerTest {

    @Inject
    MixedRequestHandler mixedRequestHandler;
    
    @Inject 
    UserRepository userRepository;
    
    @Inject
    TimelineDataGapRepository timelineDataGapRepository;
    
    @Inject
    TimelineStayRepository timelineStayRepository;
    
    @Inject
    TimelineTripRepository timelineTripRepository;
    
    @Inject
    GpsPointRepository gpsPointRepository;
    
    private UUID testUserId;
    private UserEntity testUser;
    
    @BeforeEach
    @Transactional
    void setUp() {
        // Create test user
        testUser = new UserEntity();
        testUser.setEmail("test-mixed-handler-unit@geopulse.app");
        testUser.setFullName("Test Mixed Handler Unit User");
        testUser.setPasswordHash("test-hash");
        testUser.setActive(true);
        testUser.setCreatedAt(Instant.now());
        userRepository.persistAndFlush(testUser);
        testUserId = testUser.getId();
    }
    
    @AfterEach
    @Transactional  
    void tearDown() {
        // Clean up test data
        timelineDataGapRepository.delete("user.email = ?1", testUser.getEmail());
        timelineStayRepository.delete("user.email = ?1", testUser.getEmail());
        timelineTripRepository.delete("user.email = ?1", testUser.getEmail());
        gpsPointRepository.delete("user.email = ?1", testUser.getEmail());
        userRepository.delete("email = ?1", testUser.getEmail());
    }

    /**
     * Test that today's timeline data gaps are capped at current time (we don't know the future).
     * 
     * Scenario:
     * - Request today's timeline from now-1hour to now+3hours
     * - No GPS data exists (simulated by using a random user ID)
     * - Expected: Data gap should end at "now", not at requested end time
     * 
     * This test should FAIL before the fix and PASS after the fix.
     */
    @Test
    @Transactional
    void shouldCapTodayDataGapAtCurrentTime_WhenNoGpsDataExists() {
        // Arrange - testUserId is created in setUp(), no GPS data exists for this user
        Instant now = Instant.now();
        Instant requestStart = now.minus(1, ChronoUnit.HOURS);  // 1 hour ago
        Instant requestEnd = now.plus(3, ChronoUnit.HOURS);     // 3 hours from now
        
        System.out.println("Test scenario:");
        System.out.println("  Current time: " + now);
        System.out.println("  Request start: " + requestStart);
        System.out.println("  Request end: " + requestEnd);
        System.out.println("  Expected gap end: " + now + " (current time)");
        System.out.println("  Actual request end: " + requestEnd + " (should NOT be used)");
        
        // Act
        MovementTimelineDTO timeline = mixedRequestHandler.handle(testUserId, requestStart, requestEnd);
        
        // Assert - Basic structure
        assertNotNull(timeline);
        assertEquals(testUserId, timeline.getUserId());
        assertEquals(0, timeline.getStaysCount());
        assertEquals(0, timeline.getTripsCount());
        assertEquals(1, timeline.getDataGapsCount());
        
        // Assert - Data gap details
        var dataGap = timeline.getDataGaps().get(0);
        assertEquals(requestStart, dataGap.getStartTime());
        
        // CRITICAL TEST: Gap should end at current time, NOT at requested end time
        // This assertion should FAIL before fix and PASS after fix
        long timeDiffSeconds = Math.abs(ChronoUnit.SECONDS.between(dataGap.getEndTime(), now));
        assertTrue(timeDiffSeconds <= 5, 
            "Data gap end time should be capped at current time (we don't know the future). " +
            "Expected: ~" + now + ", Actual: " + dataGap.getEndTime() + ", Diff: " + timeDiffSeconds + "s");
            
        // Additional assertion: Gap should NOT extend to requested end time
        assertTrue(dataGap.getEndTime().isBefore(requestEnd),
            "Data gap should NOT extend to requested end time (that would be predicting the future). " +
            "Gap end: " + dataGap.getEndTime() + ", Request end: " + requestEnd);
            
        System.out.println("Results:");
        System.out.println("  Actual gap start: " + dataGap.getStartTime());
        System.out.println("  Actual gap end: " + dataGap.getEndTime());
        System.out.println("  Gap duration: " + dataGap.getDurationMinutes() + " minutes");
        System.out.println("  Data source: " + timeline.getDataSource());
    }

    /**
     * Test that adjacent data gaps from past and today timelines are merged into a single gap.
     * 
     * Scenario:
     * - Request spans yesterday 21:00 to today 10:00 (current time)
     * - No GPS data exists anywhere (simulated by random user ID)
     * - Expected: Single gap covering full requested range
     * - Current bug: Gap only covers today portion (00:00 to 10:00), missing past portion
     * 
     * This test should FAIL before fix and PASS after fix.
     */
    @Test
    @Transactional
    void shouldMergeAdjacentDataGaps_WhenPastAndTodayBothHaveGaps() {
        // Arrange - Request spanning past and today  
        // testUserId is created in setUp(), no GPS data exists for this user
        Instant now = Instant.now();
        
        // Create a request that spans exactly from yesterday night to current time
        // to reproduce the user's exact issue: Aug 23 21:00 → Aug 24 current time
        Instant yesterdayNight = now.atZone(java.time.ZoneOffset.UTC)
            .toLocalDate()
            .minusDays(1)
            .atTime(21, 0) // 21:00 yesterday
            .atZone(java.time.ZoneOffset.UTC)
            .toInstant();
        Instant requestStart = yesterdayNight;
        Instant requestEnd = now; // Current time
        
        System.out.println("Adjacent gap merging test scenario:");
        System.out.println("  Current time: " + now);
        System.out.println("  Request start: " + requestStart + " (should be ~yesterday 21:00)");
        System.out.println("  Request end: " + requestEnd + " (current time)");
        System.out.println("  Expected: Single gap covering full range");
        
        // Act
        MovementTimelineDTO timeline = mixedRequestHandler.handle(testUserId, requestStart, requestEnd);
        
        // Assert - Basic structure
        assertNotNull(timeline);
        assertEquals(testUserId, timeline.getUserId());
        assertEquals(0, timeline.getStaysCount());
        assertEquals(0, timeline.getTripsCount());
        assertEquals(TimelineDataSource.MIXED, timeline.getDataSource());
        
        // CRITICAL TEST: Should have exactly one merged gap covering full request range
        // This assertion should FAIL before fix (might have 2 gaps or wrong start time)
        assertEquals(1, timeline.getDataGapsCount(), 
            "Should have exactly one merged gap, but found " + timeline.getDataGapsCount() + " gaps");
        
        var gap = timeline.getDataGaps().get(0);
        
        // CRITICAL TEST: Gap should start at request start time, not at start of today
        assertEquals(requestStart, gap.getStartTime(),
            "Data gap should start at request start time (past), not at start of today. " +
            "Expected: " + requestStart + ", Actual: " + gap.getStartTime());
            
        // Verify gap ends at current time (this should already work from previous fix)
        long timeDiffSeconds = Math.abs(ChronoUnit.SECONDS.between(gap.getEndTime(), requestEnd));
        assertTrue(timeDiffSeconds <= 5,
            "Data gap should end at request end time (current time). " +
            "Expected: ~" + requestEnd + ", Actual: " + gap.getEndTime() + ", Diff: " + timeDiffSeconds + "s");
        
        System.out.println("Results:");
        System.out.println("  Number of gaps: " + timeline.getDataGapsCount());
        System.out.println("  Gap start: " + gap.getStartTime());
        System.out.println("  Gap end: " + gap.getEndTime());
        System.out.println("  Gap duration: " + gap.getDurationMinutes() + " minutes");
        System.out.println("  Expected duration: ~" + ChronoUnit.MINUTES.between(requestStart, requestEnd) + " minutes");
    }

    /**
     * Test that past timeline data gaps respect the full requested range.
     * This is the control test - past behavior should remain unchanged.
     */
    @Test
    @Transactional
    void shouldRespectFullRequestedRange_ForPastTimelineRequests() {
        // Arrange - Request entirely in the past
        // testUserId is created in setUp(), no GPS data exists for this user
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant requestStart = yesterday.minus(2, ChronoUnit.HOURS);
        Instant requestEnd = yesterday.minus(1, ChronoUnit.HOURS);
        
        System.out.println("Past request test scenario:");
        System.out.println("  Request start: " + requestStart);
        System.out.println("  Request end: " + requestEnd);
        
        // Act
        MovementTimelineDTO timeline = mixedRequestHandler.handle(testUserId, requestStart, requestEnd);
        
        // Assert - For past requests, gap should cover full requested range
        assertNotNull(timeline);
        assertEquals(1, timeline.getDataGapsCount());
        
        var dataGap = timeline.getDataGaps().get(0);
        assertEquals(requestStart, dataGap.getStartTime());
        assertEquals(requestEnd, dataGap.getEndTime());
        assertEquals(TimelineDataSource.CACHED, timeline.getDataSource());
        
        System.out.println("Past request results:");
        System.out.println("  Gap start: " + dataGap.getStartTime());
        System.out.println("  Gap end: " + dataGap.getEndTime());
        System.out.println("  Data source: " + timeline.getDataSource());
    }
}