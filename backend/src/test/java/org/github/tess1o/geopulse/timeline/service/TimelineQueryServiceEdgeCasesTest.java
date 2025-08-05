package org.github.tess1o.geopulse.timeline.service;

import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.timeline.assembly.TimelineDataService;
import org.github.tess1o.geopulse.timeline.assembly.TimelineService;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Edge case and error handling tests for TimelineQueryService.
 * Tests boundary conditions, null handling, and error scenarios.
 */
@ExtendWith(MockitoExtension.class)
class TimelineQueryServiceEdgeCasesTest {

    @Mock private TimelineService timelineGenerationService;
    @Mock private TimelineDataService timelineDataService;
    @Mock private TimelineCacheService timelineCacheService;

    private TimelineQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new TimelineQueryService();
        
        // Inject mocks using reflection
        try {
            var generationField = TimelineQueryService.class.getDeclaredField("timelineGenerationService");
            generationField.setAccessible(true);
            generationField.set(queryService, timelineGenerationService);
            
            var dataServiceField = TimelineQueryService.class.getDeclaredField("timelineDataService");
            dataServiceField.setAccessible(true);
            dataServiceField.set(queryService, timelineDataService);
            
            var cacheServiceField = TimelineQueryService.class.getDeclaredField("timelineCacheService");
            cacheServiceField.setAccessible(true);
            cacheServiceField.set(queryService, timelineCacheService);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mocks", e);
        }
    }

    @Test
    void testGetTimeline_WithNullUserId_ShouldHandleGracefully() {
        // Arrange - Use UTC consistently to match the service logic
        Instant startTime = LocalDate.now(ZoneOffset.UTC).minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endTime = startTime.plusSeconds(86400 - 1);

        // Mock to return empty GPS data when user is null 
        GpsPointPathDTO emptyGpsData = new GpsPointPathDTO(null, Collections.emptyList());
        when(timelineDataService.getGpsPointPath(any(), any(), any())).thenReturn(emptyGpsData);
        when(timelineCacheService.exists(any(), any(), any())).thenReturn(false);

        // Act - should not throw NPE, just return empty timeline
        MovementTimelineDTO result = queryService.getTimeline(null, startTime, endTime);

        // Assert - Past dates with no cache return CACHED data source for empty timelines
        assertNotNull(result);
        assertEquals(TimelineDataSource.CACHED, result.getDataSource());
        assertTrue(result.getStays().isEmpty());
        assertTrue(result.getTrips().isEmpty());
    }

    @Test
    void testGetTimeline_WithInvalidTimeRange_ShouldHandleGracefully() {
        // Arrange - Use UTC consistently and create truly invalid range (end before start)
        UUID userId = UUID.randomUUID();
        Instant startTime = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endTime = startTime.minusSeconds(86400); // End before start (yesterday)

        // With invalid range where endTime < startTime, this creates past-only scenario
        when(timelineCacheService.exists(userId, startTime, endTime)).thenReturn(false);
        GpsPointPathDTO emptyGpsData = new GpsPointPathDTO(userId, Collections.emptyList());
        when(timelineDataService.getGpsPointPath(userId, startTime, endTime)).thenReturn(emptyGpsData);

        // Act
        MovementTimelineDTO result = queryService.getTimeline(userId, startTime, endTime);

        // Assert - Invalid range still processed as past-only, returns CACHED for empty result
        assertNotNull(result);
        assertEquals(TimelineDataSource.CACHED, result.getDataSource());
    }

    @Test
    void testGetTimeline_ExceptionInLiveGeneration_ShouldReturnEmptyTimeline() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Instant todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant todayEnd = todayStart.plusSeconds(86400 - 1);

        when(timelineGenerationService.getMovementTimeline(userId, todayStart, todayEnd))
                .thenThrow(new RuntimeException("GPS service unavailable"));

        // Act - exceptions are caught and empty timeline returned
        MovementTimelineDTO result = queryService.getTimeline(userId, todayStart, todayEnd);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
        assertTrue(result.getStays().isEmpty());
        assertTrue(result.getTrips().isEmpty());
    }

    @Test
    void testGetTimeline_ExceptionInCacheService_ShouldPropagateException() {
        // Arrange - Use UTC consistently
        UUID userId = UUID.randomUUID();
        Instant pastStart = LocalDate.now(ZoneOffset.UTC).minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant pastEnd = pastStart.plusSeconds(86400 - 1);

        when(timelineCacheService.exists(userId, pastStart, pastEnd))
                .thenThrow(new RuntimeException("Database connection error"));

        // Act & Assert - cache exceptions in past timeline requests should propagate
        assertThrows(RuntimeException.class, () -> {
            queryService.getTimeline(userId, pastStart, pastEnd);
        });
    }

    @Test
    void testGetTimeline_EmptyGpsData_ShouldReturnEmptyTimeline() {
        // Arrange - Use UTC consistently
        UUID userId = UUID.randomUUID();
        Instant pastStart = LocalDate.now(ZoneOffset.UTC).minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant pastEnd = pastStart.plusSeconds(86400 - 1);

        when(timelineCacheService.exists(userId, pastStart, pastEnd)).thenReturn(false);
        
        GpsPointPathDTO emptyGpsData = new GpsPointPathDTO(userId, Collections.emptyList());
        when(timelineDataService.getGpsPointPath(userId, pastStart, pastEnd))
                .thenReturn(emptyGpsData);

        // Act
        MovementTimelineDTO result = queryService.getTimeline(userId, pastStart, pastEnd);

        // Assert - Past date with no GPS data returns CACHED empty timeline
        assertNotNull(result);
        assertEquals(TimelineDataSource.CACHED, result.getDataSource());
        assertTrue(result.getStays().isEmpty());
        assertTrue(result.getTrips().isEmpty());
        verify(timelineGenerationService, never()).getMovementTimeline(any(), any(), any());
    }

    @Test
    void testGetTimeline_ExactMidnight_ShouldHandleCorrectly() {
        // Arrange
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant exactMidnight = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = exactMidnight.plusSeconds(86400 - 1);

        MovementTimelineDTO liveTimeline = new MovementTimelineDTO(userId);
        liveTimeline.setDataSource(TimelineDataSource.LIVE);
        when(timelineGenerationService.getMovementTimeline(userId, exactMidnight, endOfDay))
                .thenReturn(liveTimeline);

        // Act
        MovementTimelineDTO result = queryService.getTimeline(userId, exactMidnight, endOfDay);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
        verify(timelineGenerationService).getMovementTimeline(userId, exactMidnight, endOfDay);
    }

    @Test
    void testGetTimeline_SpanningMultipleDays_ShouldHandleCorrectly() {
        // Arrange
        UUID userId = UUID.randomUUID();
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        
        Instant requestStart = yesterday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant requestEnd = today.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();

        // Mock past timeline (cached)
        MovementTimelineDTO pastTimeline = new MovementTimelineDTO(userId);
        pastTimeline.setDataSource(TimelineDataSource.CACHED);
        when(timelineCacheService.exists(eq(userId), any(), any())).thenReturn(true);
        when(timelineCacheService.get(eq(userId), any(), any())).thenReturn(pastTimeline);
        
        // Mock today timeline (live)
        MovementTimelineDTO todayTimeline = new MovementTimelineDTO(userId);
        todayTimeline.setDataSource(TimelineDataSource.LIVE);
        when(timelineGenerationService.getMovementTimeline(eq(userId), any(), any()))
                .thenReturn(todayTimeline);

        // Act
        MovementTimelineDTO result = queryService.getTimeline(userId, requestStart, requestEnd);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.MIXED, result.getDataSource());
        verify(timelineCacheService).exists(eq(userId), any(), any());
        verify(timelineGenerationService).getMovementTimeline(eq(userId), any(), any());
    }

    @Test
    void testGetTimeline_VeryShortTimeRange_ShouldHandleCorrectly() {
        // Arrange - Use UTC consistently to match service logic
        UUID userId = UUID.randomUUID();
        Instant start = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = start.plusSeconds(1); // 1 second range - still today

        MovementTimelineDTO liveTimeline = new MovementTimelineDTO(userId);
        liveTimeline.setDataSource(TimelineDataSource.LIVE);
        when(timelineGenerationService.getMovementTimeline(userId, start, end))
                .thenReturn(liveTimeline);

        // Act
        MovementTimelineDTO result = queryService.getTimeline(userId, start, end);

        // Assert - Today's date should generate live timeline
        assertNotNull(result);
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
        verify(timelineGenerationService).getMovementTimeline(userId, start, end);
    }
}