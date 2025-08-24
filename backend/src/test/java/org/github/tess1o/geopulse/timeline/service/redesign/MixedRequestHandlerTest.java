package org.github.tess1o.geopulse.timeline.service.redesign;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;
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
    void shouldCapTodayDataGapAtCurrentTime_WhenNoGpsDataExists() {
        // Arrange
        UUID testUserId = UUID.randomUUID(); // Random user = no GPS data
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
     * Test that past timeline data gaps respect the full requested range.
     * This is the control test - past behavior should remain unchanged.
     */
    @Test
    void shouldRespectFullRequestedRange_ForPastTimelineRequests() {
        // Arrange - Request entirely in the past
        UUID testUserId = UUID.randomUUID(); // Random user = no GPS data
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