package org.github.tess1o.geopulse.timeline.service;

import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.timeline.assembly.TimelineService;
import org.github.tess1o.geopulse.timeline.mapper.TimelinePersistenceMapper;
import org.github.tess1o.geopulse.timeline.model.*;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
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
 * Unit tests for TimelineQueryService focusing on the integration with TimelineRegenerationService
 * and the smart delegation logic for timeline regeneration.
 */
@ExtendWith(MockitoExtension.class)
@Slf4j
class TimelineQueryServiceTest {

    @Mock private TimelineStayRepository stayRepository;
    @Mock private TimelineTripRepository tripRepository;
    @Mock private TimelineVersionService versionService;
    @Mock private TimelinePersistenceService persistenceService;
    @Mock private TimelinePersistenceMapper persistenceMapper;
    @Mock private TimelineService liveTimelineService;
    @Mock private TimelineRegenerationService regenerationService;

    private TimelineQueryService timelineQueryService;
    private UUID testUserId;
    private Instant testStartTime;
    private Instant testEndTime;

    @BeforeEach
    void setUp() {
        timelineQueryService = new TimelineQueryService();
        
        // Use reflection to inject mocks since TimelineQueryService uses field injection
        try {
            var stayRepoField = TimelineQueryService.class.getDeclaredField("stayRepository");
            stayRepoField.setAccessible(true);
            stayRepoField.set(timelineQueryService, stayRepository);
            
            var tripRepoField = TimelineQueryService.class.getDeclaredField("tripRepository");
            tripRepoField.setAccessible(true);
            tripRepoField.set(timelineQueryService, tripRepository);
            
            var versionServiceField = TimelineQueryService.class.getDeclaredField("versionService");
            versionServiceField.setAccessible(true);
            versionServiceField.set(timelineQueryService, versionService);
            
            var persistenceServiceField = TimelineQueryService.class.getDeclaredField("persistenceService");
            persistenceServiceField.setAccessible(true);
            persistenceServiceField.set(timelineQueryService, persistenceService);
            
            var mapperField = TimelineQueryService.class.getDeclaredField("persistenceMapper");
            mapperField.setAccessible(true);
            mapperField.set(timelineQueryService, persistenceMapper);
            
            var liveServiceField = TimelineQueryService.class.getDeclaredField("liveTimelineService");
            liveServiceField.setAccessible(true);
            liveServiceField.set(timelineQueryService, liveTimelineService);
            
            var regenServiceField = TimelineQueryService.class.getDeclaredField("regenerationService");
            regenServiceField.setAccessible(true);
            regenServiceField.set(timelineQueryService, regenerationService);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mocks", e);
        }

        testUserId = UUID.randomUUID();
        testStartTime = LocalDate.of(2025, 6, 4).atStartOfDay(ZoneOffset.UTC).toInstant();
        testEndTime = LocalDate.of(2025, 6, 5).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    @Test
    void testGetTimeline_TodayDate_ShouldGenerateLiveTimeline() {
        // Arrange
        Instant today = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant todayEnd = today.plusSeconds(86400);
        
        MovementTimelineDTO expectedTimeline = createMockTimeline(testUserId, TimelineDataSource.LIVE);
        when(liveTimelineService.getMovementTimeline(testUserId, today, todayEnd))
                .thenReturn(expectedTimeline);

        // Act
        MovementTimelineDTO result = timelineQueryService.getTimeline(testUserId, today, todayEnd);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.LIVE, result.getDataSource());
        assertFalse(result.getIsStale());
        verify(liveTimelineService).getMovementTimeline(testUserId, today, todayEnd);
        
        // Should not interact with persistence layer for today's data
        verify(stayRepository, never()).findByUserAndDateRange(any(), any(), any());
        verify(regenerationService, never()).regenerateTimeline(any(), any());
    }

    @Test
    void testGetTimeline_PastDateWithValidCache_ShouldReturnCachedData() {
        // Arrange
        List<TimelineStayEntity> cachedStays = List.of(createMockStayEntity());
        List<TimelineTripEntity> cachedTrips = List.of(createMockTripEntity());
        String currentVersion = "version-1.0";
        
        when(stayRepository.findByUserAndDateRange(testUserId, testStartTime, testEndTime))
                .thenReturn(cachedStays);
        when(tripRepository.findByUserAndDateRange(testUserId, testStartTime, testEndTime))
                .thenReturn(cachedTrips);
        when(versionService.generateTimelineVersion(testUserId, testStartTime))
                .thenReturn(currentVersion);
        
        // Mock valid cache data
        cachedStays.get(0).setTimelineVersion(currentVersion);
        cachedStays.get(0).setIsStale(false);
        cachedTrips.get(0).setTimelineVersion(currentVersion);
        cachedTrips.get(0).setIsStale(false);
        
        MovementTimelineDTO expectedTimeline = createMockTimeline(testUserId, TimelineDataSource.CACHED);
        lenient().when(persistenceMapper.toMovementTimelineDTO(testUserId, cachedStays, cachedTrips))
                .thenReturn(expectedTimeline);

        // Act
        MovementTimelineDTO result = timelineQueryService.getTimeline(testUserId, testStartTime, testEndTime);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.CACHED, result.getDataSource());
        
        // Should not trigger regeneration for valid cache
        verify(regenerationService, never()).regenerateTimeline(any(), any());
        verify(liveTimelineService, never()).getMovementTimeline(any(), any(), any());
    }

    @Test
    void testGetTimeline_PastDateWithStaleCache_ShouldDelegateToRegenerationService() {
        // Arrange
        List<TimelineStayEntity> staleStays = List.of(createMockStayEntity());
        List<TimelineTripEntity> staleTrips = List.of(createMockTripEntity());
        String currentVersion = "version-2.0";
        String cachedVersion = "version-1.0";
        
        when(stayRepository.findByUserAndDateRange(testUserId, testStartTime, testEndTime))
                .thenReturn(staleStays);
        when(tripRepository.findByUserAndDateRange(testUserId, testStartTime, testEndTime))
                .thenReturn(staleTrips);
        when(versionService.generateTimelineVersion(testUserId, testStartTime))
                .thenReturn(currentVersion);
        
        // Mock stale cache data (version mismatch)
        staleStays.get(0).setTimelineVersion(cachedVersion);
        staleStays.get(0).setIsStale(false);
        staleTrips.get(0).setTimelineVersion(cachedVersion);
        staleTrips.get(0).setIsStale(false);
        
        MovementTimelineDTO regeneratedTimeline = createMockTimeline(testUserId, TimelineDataSource.CACHED);
        when(regenerationService.regenerateTimeline(testUserId, testStartTime))
                .thenReturn(regeneratedTimeline);

        // Act
        MovementTimelineDTO result = timelineQueryService.getTimeline(testUserId, testStartTime, testEndTime);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.CACHED, result.getDataSource());
        
        // Should delegate to regeneration service for single day
        verify(regenerationService).regenerateTimeline(testUserId, testStartTime);
        verify(liveTimelineService, never()).getMovementTimeline(any(), any(), any());
    }

    @Test
    void testGetTimeline_PastDateWithMultipleDays_ShouldFallbackToDirectGeneration() {
        // Arrange - Multi-day request (more than 1 day)
        Instant multiDayEnd = testStartTime.plusSeconds(2 * 86400); // 2 days later
        
        List<TimelineStayEntity> staleStays = List.of(createMockStayEntity());
        List<TimelineTripEntity> staleTrips = List.of(createMockTripEntity());
        String currentVersion = "version-2.0";
        String cachedVersion = "version-1.0";
        
        when(stayRepository.findByUserAndDateRange(testUserId, testStartTime, multiDayEnd))
                .thenReturn(staleStays);
        when(tripRepository.findByUserAndDateRange(testUserId, testStartTime, multiDayEnd))
                .thenReturn(staleTrips);
        when(versionService.generateTimelineVersion(testUserId, testStartTime))
                .thenReturn(currentVersion);
        
        // Mock stale cache data
        staleStays.get(0).setTimelineVersion(cachedVersion);
        staleTrips.get(0).setTimelineVersion(cachedVersion);
        
        MovementTimelineDTO liveTimeline = createMockTimeline(testUserId, TimelineDataSource.LIVE);
        when(liveTimelineService.getMovementTimeline(testUserId, testStartTime, multiDayEnd))
                .thenReturn(liveTimeline);
        when(persistenceService.shouldPersistTimeline(testStartTime, multiDayEnd))
                .thenReturn(true);

        // Act
        MovementTimelineDTO result = timelineQueryService.getTimeline(testUserId, testStartTime, multiDayEnd);

        // Assert
        assertNotNull(result);
        
        // Should fall back to direct generation for multi-day requests
        verify(liveTimelineService).getMovementTimeline(testUserId, testStartTime, multiDayEnd);
        // Should not use regeneration service for multi-day requests (current limitation)
        verify(regenerationService, never()).regenerateTimeline(any(), any());
    }

    @Test
    void testForceRegenerateTimeline_SingleDay_ShouldDelegateToRegenerationService() {
        // Arrange
        MovementTimelineDTO regeneratedTimeline = createMockTimeline(testUserId, TimelineDataSource.CACHED);
        when(regenerationService.regenerateTimeline(testUserId, testStartTime))
                .thenReturn(regeneratedTimeline);

        // Act
        MovementTimelineDTO result = timelineQueryService.forceRegenerateTimeline(testUserId, testStartTime, testEndTime);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.CACHED, result.getDataSource());
        
        // Should clear existing cache and delegate to regeneration service
        verify(persistenceService).clearTimelineForRange(testUserId, testStartTime, testEndTime);
        verify(regenerationService).regenerateTimeline(testUserId, testStartTime);
    }

    @Test
    void testForceRegenerateTimeline_MultiDay_ShouldFallbackToDirectGeneration() {
        // Arrange
        Instant multiDayEnd = testStartTime.plusSeconds(3 * 86400); // 3 days
        
        MovementTimelineDTO liveTimeline = createMockTimeline(testUserId, TimelineDataSource.LIVE);
        when(liveTimelineService.getMovementTimeline(testUserId, testStartTime, multiDayEnd))
                .thenReturn(liveTimeline);
        when(persistenceService.shouldPersistTimeline(testStartTime, multiDayEnd))
                .thenReturn(true);

        // Act
        MovementTimelineDTO result = timelineQueryService.forceRegenerateTimeline(testUserId, testStartTime, multiDayEnd);

        // Assert
        assertNotNull(result);
        
        // Should clear cache and use direct generation for multi-day
        verify(persistenceService).clearTimelineForRange(testUserId, testStartTime, multiDayEnd);
        verify(liveTimelineService).getMovementTimeline(testUserId, testStartTime, multiDayEnd);
        verify(regenerationService, never()).regenerateTimeline(any(), any());
    }

    @Test
    void testGetTimelineDataSource_PastDateWithCache_ShouldReturnCached() {
        // Arrange
        when(persistenceService.shouldPersistTimeline(testStartTime)).thenReturn(true);
        when(persistenceService.hasPersistedTimelineForDate(testUserId, testStartTime)).thenReturn(true);
        when(versionService.generateTimelineVersion(testUserId, testStartTime)).thenReturn("version-1.0");
        when(persistenceService.getPersistedTimelineVersion(testUserId, testStartTime)).thenReturn("version-1.0");

        // Act
        TimelineDataSource result = timelineQueryService.getTimelineDataSource(testUserId, testStartTime);

        // Assert
        assertEquals(TimelineDataSource.CACHED, result);
    }

    @Test
    void testGetTimelineDataSource_PastDateWithStaleCache_ShouldReturnRegenerating() {
        // Arrange
        when(persistenceService.shouldPersistTimeline(testStartTime)).thenReturn(true);
        when(persistenceService.hasPersistedTimelineForDate(testUserId, testStartTime)).thenReturn(true);
        when(versionService.generateTimelineVersion(testUserId, testStartTime)).thenReturn("version-2.0");
        when(persistenceService.getPersistedTimelineVersion(testUserId, testStartTime)).thenReturn("version-1.0");

        // Act
        TimelineDataSource result = timelineQueryService.getTimelineDataSource(testUserId, testStartTime);

        // Assert
        assertEquals(TimelineDataSource.REGENERATING, result);
    }

    @Test
    void testGetTimelineDataSource_TodayDate_ShouldReturnLive() {
        // Arrange
        Instant today = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        when(persistenceService.shouldPersistTimeline(today)).thenReturn(false);

        // Act
        TimelineDataSource result = timelineQueryService.getTimelineDataSource(testUserId, today);

        // Assert
        assertEquals(TimelineDataSource.LIVE, result);
    }

    private MovementTimelineDTO createMockTimeline(UUID userId, TimelineDataSource dataSource) {
        MovementTimelineDTO timeline = new MovementTimelineDTO(userId);
        timeline.setDataSource(dataSource);
        timeline.setLastUpdated(Instant.now());
        timeline.setIsStale(false);
        return timeline;
    }

    private TimelineStayEntity createMockStayEntity() {
        TimelineStayEntity stay = new TimelineStayEntity();
        stay.setId(1L);
        stay.setTimestamp(testStartTime);
        stay.setLatitude(52.520008);
        stay.setLongitude(13.404954);
        stay.setLocationName("Test Location");
        stay.setStayDuration(60);
        stay.setLocationSource(LocationSource.GEOCODING);
        stay.setIsStale(false);
        stay.setTimelineVersion("version-1.0");
        stay.setLastUpdated(Instant.now());
        
        // Mock user
        UserEntity user = new UserEntity();
        user.setId(testUserId);
        stay.setUser(user);
        
        return stay;
    }

    private TimelineTripEntity createMockTripEntity() {
        TimelineTripEntity trip = new TimelineTripEntity();
        trip.setId(1L);
        trip.setTimestamp(testStartTime);
        trip.setStartLatitude(52.520008);
        trip.setStartLongitude(13.404954);
        trip.setEndLatitude(52.521008);
        trip.setEndLongitude(13.405954);
        trip.setTripDuration(30);
        trip.setDistanceKm(1.5);
        trip.setMovementType("WALKING");
        trip.setTimelineVersion("version-1.0");
        trip.setIsStale(false);
        trip.setLastUpdated(Instant.now());
        
        // Mock user
        UserEntity user = new UserEntity();
        user.setId(testUserId);
        trip.setUser(user);
        
        return trip;
    }
}