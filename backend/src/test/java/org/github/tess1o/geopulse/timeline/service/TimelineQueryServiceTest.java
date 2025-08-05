package org.github.tess1o.geopulse.timeline.service;

import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SimpleTimelineQueryService.
 * Tests the core logic without complex version checking or expansion.
 */
@ExtendWith(MockitoExtension.class)
@Slf4j
class TimelineQueryServiceTest {

    @Mock private TimelineService timelineGenerationService;
    @Mock private TimelineDataService timelineDataService;
    @Mock private TimelineCacheService timelineCacheService;

    private TimelineQueryService queryService;
    private UUID testUserId;
    private Instant testStartTime;
    private Instant testEndTime;

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

        testUserId = UUID.randomUUID();
        // Use past date for most tests
        testStartTime = LocalDate.now(ZoneOffset.UTC).minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        testEndTime = testStartTime.plusSeconds(86400 - 1); // End of same day
    }

    @Test
    void testGetTimeline_TodayOnly_ShouldGenerateLive() {
        // Arrange - Today's date
        Instant todayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant todayEnd = todayStart.plusSeconds(86400 - 1);
        
        MovementTimelineDTO expectedTimeline = createMockTimeline(testUserId, TimelineDataSource.LIVE);
        when(timelineGenerationService.getMovementTimeline(testUserId, todayStart, todayEnd))
                .thenReturn(expectedTimeline);

        // Act
        MovementTimelineDTO result = queryService.getTimeline(testUserId, todayStart, todayEnd);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());

        verify(timelineGenerationService).getMovementTimeline(testUserId, todayStart, todayEnd);
        verify(timelineCacheService, never()).exists(any(), any(), any());
    }

    @Test
    void testGetTimeline_PastWithCache_ShouldReturnCached() {
        // Arrange
        when(timelineCacheService.exists(testUserId, testStartTime, testEndTime))
                .thenReturn(true);
        
        MovementTimelineDTO cachedTimeline = createMockTimeline(testUserId, TimelineDataSource.CACHED);
        when(timelineCacheService.get(testUserId, testStartTime, testEndTime))
                .thenReturn(cachedTimeline);

        // Act
        MovementTimelineDTO result = queryService.getTimeline(testUserId, testStartTime, testEndTime);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.CACHED, result.getDataSource());
        
        verify(timelineCacheService).exists(testUserId, testStartTime, testEndTime);
        verify(timelineCacheService).get(testUserId, testStartTime, testEndTime);
        verify(timelineGenerationService, never()).getMovementTimeline(any(), any(), any());
    }

    @Test
    void testGetTimeline_PastWithoutCacheButWithGps_ShouldGenerateAndCache() {
        // Arrange
        when(timelineCacheService.exists(testUserId, testStartTime, testEndTime))
                .thenReturn(false);
        
        // Mock GPS data exists
        GpsPointPathDTO gpsData = createMockGpsDataWithPoints();
        when(timelineDataService.getGpsPointPath(testUserId, testStartTime, testEndTime))
                .thenReturn(gpsData);
        
        MovementTimelineDTO generatedTimeline = createMockTimeline(testUserId, TimelineDataSource.CACHED);
        when(timelineGenerationService.getMovementTimeline(testUserId, testStartTime, testEndTime))
                .thenReturn(generatedTimeline);

        // Act
        MovementTimelineDTO result = queryService.getTimeline(testUserId, testStartTime, testEndTime);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.CACHED, result.getDataSource());
        
        verify(timelineCacheService).exists(testUserId, testStartTime, testEndTime);
        verify(timelineDataService).getGpsPointPath(testUserId, testStartTime, testEndTime);
        verify(timelineGenerationService).getMovementTimeline(testUserId, testStartTime, testEndTime);
        verify(timelineCacheService).save(testUserId, testStartTime, testEndTime, generatedTimeline);
    }

    @Test
    void testGetTimeline_PastWithoutCacheOrGps_ShouldReturnEmpty() {
        // Arrange
        when(timelineCacheService.exists(testUserId, testStartTime, testEndTime))
                .thenReturn(false);
        
        // Mock no GPS data
        GpsPointPathDTO emptyGpsData = new GpsPointPathDTO(testUserId, List.of());
        when(timelineDataService.getGpsPointPath(testUserId, testStartTime, testEndTime))
                .thenReturn(emptyGpsData);

        // Act
        MovementTimelineDTO result = queryService.getTimeline(testUserId, testStartTime, testEndTime);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.CACHED, result.getDataSource());
        assertTrue(result.getStays().isEmpty());
        assertTrue(result.getTrips().isEmpty());
        
        verify(timelineCacheService).exists(testUserId, testStartTime, testEndTime);
        verify(timelineDataService).getGpsPointPath(testUserId, testStartTime, testEndTime);
        verify(timelineGenerationService, never()).getMovementTimeline(any(), any(), any());
        verify(timelineCacheService, never()).save(any(), any(), any(), any());
    }

    @Test
    void testGetTimeline_MixedPastAndToday_ShouldCombine() {
        // Arrange - Request spanning yesterday and today
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate yesterday = today.minusDays(1);
        
        Instant requestStart = yesterday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant requestEnd = today.atTime(12, 0).atZone(ZoneOffset.UTC).toInstant(); // Noon today
        
        // Mock past timeline (cached)
        MovementTimelineDTO pastTimeline = createMockTimeline(testUserId, TimelineDataSource.CACHED);
        when(timelineCacheService.exists(eq(testUserId), eq(requestStart), any()))
                .thenReturn(true);
        when(timelineCacheService.get(eq(testUserId), eq(requestStart), any()))
                .thenReturn(pastTimeline);
        
        // Mock today timeline (live)
        MovementTimelineDTO todayTimeline = createMockTimeline(testUserId, TimelineDataSource.LIVE);
        when(timelineGenerationService.getMovementTimeline(eq(testUserId), any(), eq(requestEnd)))
                .thenReturn(todayTimeline);

        // Act
        MovementTimelineDTO result = queryService.getTimeline(testUserId, requestStart, requestEnd);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.MIXED, result.getDataSource());
        
        // Should have called both cache and live generation
        verify(timelineCacheService).exists(eq(testUserId), eq(requestStart), any());
        verify(timelineGenerationService).getMovementTimeline(eq(testUserId), any(), eq(requestEnd));
    }

    @Test
    void testGetTimeline_FutureDates_ShouldReturnEmpty() {
        // Arrange - Future dates
        Instant futureStart = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant futureEnd = futureStart.plusSeconds(86400 - 1);

        // Act
        MovementTimelineDTO result = queryService.getTimeline(testUserId, futureStart, futureEnd);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
        assertTrue(result.getStays().isEmpty());
        assertTrue(result.getTrips().isEmpty());
        
        // Should not call any services for future dates
        verify(timelineCacheService, never()).exists(any(), any(), any());
        verify(timelineGenerationService, never()).getMovementTimeline(any(), any(), any());
    }

    private MovementTimelineDTO createMockTimeline(UUID userId, TimelineDataSource dataSource) {
        MovementTimelineDTO timeline = new MovementTimelineDTO(userId);
        timeline.setDataSource(dataSource);
        timeline.setLastUpdated(Instant.now());
        return timeline;
    }

    private GpsPointPathDTO createMockGpsDataWithPoints() {
        GpsPointPathPointDTO point = new GpsPointPathPointDTO();
        point.setId(1L);
        point.setLongitude(-122.419);
        point.setLatitude(37.775);
        point.setTimestamp(testStartTime);
        point.setAccuracy(10.0);
        point.setAltitude(100.0);
        point.setUserId(testUserId);
        
        return new GpsPointPathDTO(testUserId, List.of(point));
    }
}