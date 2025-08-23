package org.github.tess1o.geopulse.timeline.service.redesign;

import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for TimelineRequestRouter.
 * Tests request classification and routing logic using mocked handlers.
 */
@ExtendWith(MockitoExtension.class)
class TimelineRequestRouterTest {

    @InjectMocks
    TimelineRequestRouter timelineRequestRouter;

    @Mock
    PastRequestHandler pastRequestHandler;

    @Mock
    MixedRequestHandler mixedRequestHandler;

    private UUID testUserId;
    private MovementTimelineDTO mockPastTimeline;
    private MovementTimelineDTO mockMixedTimeline;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        
        // Create mock timeline responses
        mockPastTimeline = new MovementTimelineDTO(testUserId);
        mockPastTimeline.setDataSource(TimelineDataSource.CACHED);
        mockPastTimeline.setLastUpdated(Instant.now());

        mockMixedTimeline = new MovementTimelineDTO(testUserId);
        mockMixedTimeline.setDataSource(TimelineDataSource.MIXED);
        mockMixedTimeline.setLastUpdated(Instant.now());
    }

    @Test
    @DisplayName("Past only request: Aug 1-15 (today is after Aug 15)")
    void testPastOnlyRequest() {
        // Configure mock to return response
        when(pastRequestHandler.handle(any(), any(), any())).thenReturn(mockPastTimeline);
        
        // Create past-only date range (assume today is much later)
        LocalDate startDate = LocalDate.of(2024, 8, 1);
        LocalDate endDate = LocalDate.of(2024, 8, 15);
        Instant startTime = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endTime = endDate.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();

        // Call the router
        MovementTimelineDTO result = timelineRequestRouter.getTimeline(testUserId, startTime, endTime);

        // Verify past handler was called with correct parameters
        verify(pastRequestHandler, times(1)).handle(eq(testUserId), eq(startTime), eq(endTime));
        verify(mixedRequestHandler, never()).handle(any(), any(), any());
        
        // Verify correct result returned
        assertEquals(mockPastTimeline, result);
        assertEquals(TimelineDataSource.CACHED, result.getDataSource());
    }

    @Test
    @DisplayName("Mixed request: Past dates extending to today")
    void testMixedRequest_PastToToday() {
        // Configure mock to return response
        when(mixedRequestHandler.handle(any(), any(), any())).thenReturn(mockMixedTimeline);
        
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = today.minusDays(5); // 5 days ago
        Instant startTime = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endTime = today.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();

        // Call the router
        MovementTimelineDTO result = timelineRequestRouter.getTimeline(testUserId, startTime, endTime);

        // Verify mixed handler was called with correct parameters
        verify(mixedRequestHandler, times(1)).handle(eq(testUserId), eq(startTime), eq(endTime));
        verify(pastRequestHandler, never()).handle(any(), any(), any());
        
        // Verify correct result returned
        assertEquals(mockMixedTimeline, result);
        assertEquals(TimelineDataSource.MIXED, result.getDataSource());
    }

    @Test
    @DisplayName("Today only request: Should route to mixed handler")
    void testTodayOnlyRequest() {
        // Configure mock to return response
        when(mixedRequestHandler.handle(any(), any(), any())).thenReturn(mockMixedTimeline);
        
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startTime = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endTime = today.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();

        // Call the router
        MovementTimelineDTO result = timelineRequestRouter.getTimeline(testUserId, startTime, endTime);

        // Verify mixed handler was called (today is considered "mixed")
        verify(mixedRequestHandler, times(1)).handle(eq(testUserId), eq(startTime), eq(endTime));
        verify(pastRequestHandler, never()).handle(any(), any(), any());
        
        assertEquals(mockMixedTimeline, result);
    }

    @Test
    @DisplayName("Future only request: Should return empty timeline")
    void testFutureOnlyRequest() {
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        LocalDate dayAfterTomorrow = tomorrow.plusDays(1);
        Instant startTime = tomorrow.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endTime = dayAfterTomorrow.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();

        // Call the router
        MovementTimelineDTO result = timelineRequestRouter.getTimeline(testUserId, startTime, endTime);

        // Verify no handlers were called
        verify(pastRequestHandler, never()).handle(any(), any(), any());
        verify(mixedRequestHandler, never()).handle(any(), any(), any());
        
        // Verify empty timeline returned
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
        assertTrue(result.getStays().isEmpty());
        assertTrue(result.getTrips().isEmpty());
        assertNotNull(result.getLastUpdated());
    }

    @Test
    @DisplayName("Single moment request: Same start and end time")
    void testSingleMomentRequest() {
        // Configure mock to return response
        when(pastRequestHandler.handle(any(), any(), any())).thenReturn(mockPastTimeline);
        
        LocalDate pastDate = LocalDate.of(2024, 8, 15);
        Instant singleMoment = pastDate.atTime(12, 0).atZone(ZoneOffset.UTC).toInstant();

        // Call the router with same start/end time
        MovementTimelineDTO result = timelineRequestRouter.getTimeline(testUserId, singleMoment, singleMoment);

        // Should be classified as past request since the moment is in the past
        verify(pastRequestHandler, times(1)).handle(eq(testUserId), eq(singleMoment), eq(singleMoment));
        assertEquals(mockPastTimeline, result);
    }

    @Test
    @DisplayName("Request classification: Analyze request types correctly")
    void testRequestTypeAnalysis() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        
        // Test past only
        Instant pastStart = today.minusDays(10).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant pastEnd = today.minusDays(1).atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
        assertEquals(TimelineRequestRouter.RequestType.PAST_ONLY, 
                    timelineRequestRouter.analyzeRequest(pastStart, pastEnd));

        // Test mixed (past to today)
        Instant mixedStart = today.minusDays(5).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant mixedEnd = today.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
        assertEquals(TimelineRequestRouter.RequestType.MIXED, 
                    timelineRequestRouter.analyzeRequest(mixedStart, mixedEnd));

        // Test today only
        Instant todayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant todayEnd = today.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
        assertEquals(TimelineRequestRouter.RequestType.MIXED, 
                    timelineRequestRouter.analyzeRequest(todayStart, todayEnd));

        // Test future only
        Instant futureStart = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant futureEnd = today.plusDays(5).atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
        assertEquals(TimelineRequestRouter.RequestType.FUTURE_ONLY, 
                    timelineRequestRouter.analyzeRequest(futureStart, futureEnd));
    }

    @Test
    @DisplayName("Parameter passing: Exact timestamps passed to handlers")
    void testExactParameterPassing() {
        // Configure mock to return response
        when(pastRequestHandler.handle(any(), any(), any())).thenReturn(mockPastTimeline);
        
        // Use precise timestamps to verify exact parameter passing
        Instant preciseStart = Instant.parse("2024-08-01T10:30:45.123Z");
        Instant preciseEnd = Instant.parse("2024-08-01T18:45:30.987Z");

        timelineRequestRouter.getTimeline(testUserId, preciseStart, preciseEnd);

        // Verify exact timestamps were passed (past only since both dates are in past)
        verify(pastRequestHandler, times(1)).handle(
            eq(testUserId), 
            eq(preciseStart), 
            eq(preciseEnd)
        );
    }

    @Test
    @DisplayName("Edge case: End of day boundary")
    void testEndOfDayBoundary() {
        // Configure mock to return response
        when(mixedRequestHandler.handle(any(), any(), any())).thenReturn(mockMixedTimeline);
        
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        Instant startTime = yesterday.atStartOfDay(ZoneOffset.UTC).toInstant();
        // End at exactly midnight (start of today)
        Instant endTime = yesterday.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        timelineRequestRouter.getTimeline(testUserId, startTime, endTime);

        // Should be mixed since endTime touches today
        verify(mixedRequestHandler, times(1)).handle(eq(testUserId), eq(startTime), eq(endTime));
        verify(pastRequestHandler, never()).handle(any(), any(), any());
    }

    @Test
    @DisplayName("Error handling: Handler exceptions are propagated")
    void testHandlerExceptionPropagation() {
        // Configure mock to throw exception
        RuntimeException testException = new RuntimeException("Handler failed");
        when(pastRequestHandler.handle(any(), any(), any())).thenThrow(testException);

        LocalDate pastDate = LocalDate.of(2024, 8, 1);
        Instant startTime = pastDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endTime = pastDate.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();

        // Exception should be propagated
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
            timelineRequestRouter.getTimeline(testUserId, startTime, endTime)
        );

        assertEquals("Handler failed", thrown.getMessage());
        verify(pastRequestHandler, times(1)).handle(any(), any(), any());
    }

    @Test
    @DisplayName("Multiple requests: Verify no state carried between calls")
    void testMultipleRequestsNoStatePersistence() {
        // Configure mocks to return responses
        when(pastRequestHandler.handle(any(), any(), any())).thenReturn(mockPastTimeline);
        when(mixedRequestHandler.handle(any(), any(), any())).thenReturn(mockMixedTimeline);
        
        // Make multiple different requests
        LocalDate pastDate = LocalDate.of(2024, 8, 1);
        Instant pastStart = pastDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant pastEnd = pastDate.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant todayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant todayEnd = today.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();

        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        // First request - past only
        timelineRequestRouter.getTimeline(userId1, pastStart, pastEnd);
        
        // Second request - today only  
        timelineRequestRouter.getTimeline(userId2, todayStart, todayEnd);

        // Verify correct handlers called with correct parameters
        verify(pastRequestHandler, times(1)).handle(eq(userId1), eq(pastStart), eq(pastEnd));
        verify(mixedRequestHandler, times(1)).handle(eq(userId2), eq(todayStart), eq(todayEnd));
    }
}