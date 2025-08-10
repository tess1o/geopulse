package org.github.tess1o.geopulse.timeline.service;

import org.github.tess1o.geopulse.timeline.mapper.TimelinePersistenceMapper;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;
import org.github.tess1o.geopulse.timeline.model.TimelineStayEntity;
import org.github.tess1o.geopulse.timeline.model.TimelineTripEntity;
import org.github.tess1o.geopulse.timeline.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.timeline.repository.TimelineTripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TimelineCacheService.
 * Tests simple cache operations without complex versioning.
 */
@ExtendWith(MockitoExtension.class)
class TimelineCacheServiceTest {

    @Mock private TimelineStayRepository stayRepository;
    @Mock private TimelineTripRepository tripRepository;
    @Mock private TimelineDataGapRepository dataGapRepository;
    @Mock private TimelinePersistenceMapper persistenceMapper;

    private TimelineCacheService cacheService;
    private UUID testUserId;
    private Instant testStartTime;
    private Instant testEndTime;

    @BeforeEach
    void setUp() {
        cacheService = new TimelineCacheService();
        
        // Inject mocks using reflection
        try {
            var stayField = TimelineCacheService.class.getDeclaredField("stayRepository");
            stayField.setAccessible(true);
            stayField.set(cacheService, stayRepository);
            
            var tripField = TimelineCacheService.class.getDeclaredField("tripRepository");
            tripField.setAccessible(true);
            tripField.set(cacheService, tripRepository);
            
            var dataGapField = TimelineCacheService.class.getDeclaredField("dataGapRepository");
            dataGapField.setAccessible(true);
            dataGapField.set(cacheService, dataGapRepository);
            
            var mapperField = TimelineCacheService.class.getDeclaredField("persistenceMapper");
            mapperField.setAccessible(true);
            mapperField.set(cacheService, persistenceMapper);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mocks", e);
        }

        testUserId = UUID.randomUUID();
        testStartTime = LocalDate.now().minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        testEndTime = testStartTime.plusSeconds(86400 - 1);
    }

    @Test
    void testExists_WithCachedData_ShouldReturnTrue() {
        // Arrange
        when(stayRepository.findByUserAndDateRange(testUserId, testStartTime, testEndTime))
                .thenReturn(List.of(new TimelineStayEntity()));

        // Act
        boolean result = cacheService.exists(testUserId, testStartTime, testEndTime);

        // Assert
        assertTrue(result);
        verify(stayRepository).findByUserAndDateRange(testUserId, testStartTime, testEndTime);
    }

    @Test
    void testExists_WithoutCachedData_ShouldReturnFalse() {
        // Arrange
        when(stayRepository.findByUserAndDateRange(testUserId, testStartTime, testEndTime))
                .thenReturn(Collections.emptyList());
        when(tripRepository.findByUserAndDateRange(testUserId, testStartTime, testEndTime))
                .thenReturn(Collections.emptyList());

        // Act
        boolean result = cacheService.exists(testUserId, testStartTime, testEndTime);

        // Assert
        assertFalse(result);
        verify(stayRepository).findByUserAndDateRange(testUserId, testStartTime, testEndTime);
        verify(tripRepository).findByUserAndDateRange(testUserId, testStartTime, testEndTime);
    }

    @Test
    void testGet_WithCachedData_ShouldReturnTimeline() {
        // Arrange
        List<TimelineStayEntity> stays = List.of(new TimelineStayEntity());
        List<TimelineTripEntity> trips = List.of(new TimelineTripEntity());
        
        when(stayRepository.findByUserAndDateRange(testUserId, testStartTime, testEndTime))
                .thenReturn(stays);
        when(tripRepository.findByUserAndDateRange(testUserId, testStartTime, testEndTime))
                .thenReturn(trips);
        when(dataGapRepository.findByUserIdAndTimeRange(testUserId, testStartTime, testEndTime))
                .thenReturn(Collections.emptyList());
        
        MovementTimelineDTO expectedTimeline = new MovementTimelineDTO(testUserId);
        expectedTimeline.setDataSource(TimelineDataSource.CACHED);
        when(persistenceMapper.toMovementTimelineDTO(eq(testUserId), eq(stays), eq(trips), any()))
                .thenReturn(expectedTimeline);

        // Act
        MovementTimelineDTO result = cacheService.get(testUserId, testStartTime, testEndTime);

        // Assert
        assertNotNull(result);
        assertEquals(TimelineDataSource.CACHED, result.getDataSource());
        assertEquals(testUserId, result.getUserId());
        verify(persistenceMapper).toMovementTimelineDTO(eq(testUserId), eq(stays), eq(trips), any());
    }

    @Test
    void testDelete_WithSpecificDates_ShouldDeleteTimelineData() {
        // Arrange
        List<LocalDate> datesToDelete = List.of(
            LocalDate.now().minusDays(1),
            LocalDate.now().minusDays(2)
        );

        // Act
        cacheService.delete(testUserId, datesToDelete);

        // Assert
        // The delete method should call the repository methods for each date
        verify(stayRepository, atLeastOnce()).findByUserAndDateRange(eq(testUserId), any(), any());
        verify(tripRepository, atLeastOnce()).findByUserAndDateRange(eq(testUserId), any(), any());
    }
}