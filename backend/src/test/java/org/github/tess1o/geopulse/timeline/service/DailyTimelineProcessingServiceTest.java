package org.github.tess1o.geopulse.timeline.service;

import io.quarkus.test.common.QuarkusTestResource;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.model.TimelineDataSource;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DailyTimelineProcessingService, focusing on the business logic
 * and error handling of timeline processing operations.
 */
@ExtendWith(MockitoExtension.class)
class DailyTimelineProcessingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TimelineQueryService timelineQueryService;

    @Mock
    private TimelinePersistenceService persistenceService;

    @InjectMocks
    private DailyTimelineProcessingService dailyProcessingService;

    private UUID testUserId;
    private UserEntity testUser;
    private LocalDate testDate;
    private Instant startOfDay;
    private Instant endOfDay;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = new UserEntity();
        testUser.setId(testUserId);
        testUser.setEmail("test@example.com");
        testUser.setActive(true);

        testDate = LocalDate.of(2025, 6, 15);
        startOfDay = testDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        endOfDay = testDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    @Test
    void testProcessUserTimeline_NewTimeline_ShouldProcessSuccessfully() {
        // Arrange
        MovementTimelineDTO timeline = createMockTimeline(TimelineDataSource.CACHED, 2, 1);
        
        when(persistenceService.hasPersistedTimelineForDate(testUserId, startOfDay)).thenReturn(false);
        when(timelineQueryService.getTimeline(testUserId, startOfDay, endOfDay)).thenReturn(timeline);

        // Act
        boolean result = dailyProcessingService.processUserTimeline(testUserId, startOfDay, endOfDay, testDate);

        // Assert
        assertTrue(result, "Should return true when timeline is processed and cached");
        verify(persistenceService).hasPersistedTimelineForDate(testUserId, startOfDay);
        verify(timelineQueryService).getTimeline(testUserId, startOfDay, endOfDay);
    }

    @Test
    void testProcessUserTimeline_ExistingTimeline_ShouldSkip() {
        // Arrange
        when(persistenceService.hasPersistedTimelineForDate(testUserId, startOfDay)).thenReturn(true);

        // Act
        boolean result = dailyProcessingService.processUserTimeline(testUserId, startOfDay, endOfDay, testDate);

        // Assert
        assertFalse(result, "Should return false when timeline already exists");
        verify(persistenceService).hasPersistedTimelineForDate(testUserId, startOfDay);
        verify(timelineQueryService, never()).getTimeline(any(), any(), any());
    }

    @Test
    void testProcessUserTimeline_NoTimelineData_ShouldSkip() {
        // Arrange
        when(persistenceService.hasPersistedTimelineForDate(testUserId, startOfDay)).thenReturn(false);
        when(timelineQueryService.getTimeline(testUserId, startOfDay, endOfDay)).thenReturn(null);

        // Act
        boolean result = dailyProcessingService.processUserTimeline(testUserId, startOfDay, endOfDay, testDate);

        // Assert
        assertFalse(result, "Should return false when no timeline data is generated");
        verify(timelineQueryService).getTimeline(testUserId, startOfDay, endOfDay);
    }

    @Test
    void testProcessUserTimeline_EmptyTimeline_ShouldSkip() {
        // Arrange
        MovementTimelineDTO emptyTimeline = createMockTimeline(TimelineDataSource.LIVE, 0, 0);
        
        when(persistenceService.hasPersistedTimelineForDate(testUserId, startOfDay)).thenReturn(false);
        when(timelineQueryService.getTimeline(testUserId, startOfDay, endOfDay)).thenReturn(emptyTimeline);

        // Act
        boolean result = dailyProcessingService.processUserTimeline(testUserId, startOfDay, endOfDay, testDate);

        // Assert
        assertFalse(result, "Should return false when timeline has no activities");
        verify(timelineQueryService).getTimeline(testUserId, startOfDay, endOfDay);
    }

    @Test
    void testProcessUserTimeline_LiveTimeline_ShouldReturnFalse() {
        // Arrange - timeline was generated live but not cached (e.g., for current day)
        MovementTimelineDTO timeline = createMockTimeline(TimelineDataSource.LIVE, 2, 1);
        
        when(persistenceService.hasPersistedTimelineForDate(testUserId, startOfDay)).thenReturn(false);
        when(timelineQueryService.getTimeline(testUserId, startOfDay, endOfDay)).thenReturn(timeline);

        // Act
        boolean result = dailyProcessingService.processUserTimeline(testUserId, startOfDay, endOfDay, testDate);

        // Assert
        assertFalse(result, "Should return false when timeline is live but not cached");
        verify(timelineQueryService).getTimeline(testUserId, startOfDay, endOfDay);
    }

    @Test
    void testProcessUserTimeline_ServiceThrowsException_ShouldPropagateException() {
        // Arrange
        when(persistenceService.hasPersistedTimelineForDate(testUserId, startOfDay)).thenReturn(false);
        when(timelineQueryService.getTimeline(testUserId, startOfDay, endOfDay))
            .thenThrow(new RuntimeException("Database connection error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            dailyProcessingService.processUserTimeline(testUserId, startOfDay, endOfDay, testDate);
        });

        assertEquals("Database connection error", exception.getMessage());
        verify(timelineQueryService).getTimeline(testUserId, startOfDay, endOfDay);
    }

    @Test
    void testProcessTimelineForDate_WithActiveUsers_ShouldProcessAll() {
        // Arrange
        List<UserEntity> activeUsers = List.of(testUser, createUser("user2@example.com"), createUser("user3@example.com"));
        MovementTimelineDTO timeline = createMockTimeline(TimelineDataSource.CACHED, 1, 1);
        
        when(userRepository.findActiveUsers()).thenReturn(activeUsers);
        when(persistenceService.hasPersistedTimelineForDate(any(), any())).thenReturn(false);
        when(timelineQueryService.getTimeline(any(), any(), any())).thenReturn(timeline);

        // Act
        DailyTimelineProcessingService.ProcessingStatistics stats = 
            dailyProcessingService.processTimelineForDate(testDate);

        // Assert
        assertNotNull(stats);
        assertEquals(testDate, stats.processedDate());
        assertEquals(3, stats.totalUsers(), "Should process all 3 users");
        assertEquals(3, stats.successfulUsers(), "All users should be successful");
        assertEquals(0, stats.failedUsers(), "No users should fail");
        
        verify(userRepository).findActiveUsers();
        verify(timelineQueryService, times(3)).getTimeline(any(), eq(startOfDay), eq(endOfDay));
    }

    @Test
    void testProcessTimelineForDate_WithMixedResults_ShouldTrackStatistics() {
        // Arrange
        UserEntity user2 = createUser("user2@example.com");
        UserEntity user3 = createUser("user3@example.com");
        List<UserEntity> activeUsers = List.of(testUser, user2, user3);
        
        MovementTimelineDTO timeline = createMockTimeline(TimelineDataSource.CACHED, 1, 1);
        
        when(userRepository.findActiveUsers()).thenReturn(activeUsers);
        when(persistenceService.hasPersistedTimelineForDate(any(), any())).thenReturn(false);
        
        // First user: successful processing
        when(timelineQueryService.getTimeline(testUserId, startOfDay, endOfDay)).thenReturn(timeline);
        
        // Second user: no timeline data (still counts as processed, but not successful)
        when(timelineQueryService.getTimeline(user2.getId(), startOfDay, endOfDay)).thenReturn(null);
        
        // Third user: throws exception
        when(timelineQueryService.getTimeline(user3.getId(), startOfDay, endOfDay))
            .thenThrow(new RuntimeException("Processing error"));

        // Act
        DailyTimelineProcessingService.ProcessingStatistics stats = 
            dailyProcessingService.processTimelineForDate(testDate);

        // Assert
        assertNotNull(stats);
        assertEquals(testDate, stats.processedDate());
        assertEquals(2, stats.totalUsers(), "Should count 2 users that were processed without exception");
        assertEquals(1, stats.successfulUsers(), "Only 1 user should be successful");
        assertEquals(1, stats.failedUsers(), "1 user should fail due to exception");
        
        verify(timelineQueryService, times(3)).getTimeline(any(), eq(startOfDay), eq(endOfDay));
    }

    @Test
    void testProcessTimelineForDate_WithNoActiveUsers_ShouldReturnZeroStats() {
        // Arrange
        when(userRepository.findActiveUsers()).thenReturn(Collections.emptyList());

        // Act
        DailyTimelineProcessingService.ProcessingStatistics stats = 
            dailyProcessingService.processTimelineForDate(testDate);

        // Assert
        assertNotNull(stats);
        assertEquals(testDate, stats.processedDate());
        assertEquals(0, stats.totalUsers());
        assertEquals(0, stats.successfulUsers());
        assertEquals(0, stats.failedUsers());
        
        verify(userRepository).findActiveUsers();
        verify(timelineQueryService, never()).getTimeline(any(), any(), any());
    }

    @Test
    void testProcessTimelineForDate_WithRepositoryException_ShouldPropagateException() {
        // Arrange
        when(userRepository.findActiveUsers()).thenThrow(new RuntimeException("Database connection error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            dailyProcessingService.processTimelineForDate(testDate);
        });

        assertEquals("Database connection error", exception.getMessage());
        verify(userRepository).findActiveUsers();
        verify(timelineQueryService, never()).getTimeline(any(), any(), any());
    }

    @Test
    void testProcessingStatistics_ToString_ShouldFormatCorrectly() {
        // Arrange
        DailyTimelineProcessingService.ProcessingStatistics stats = 
            new DailyTimelineProcessingService.ProcessingStatistics(testDate, 10, 8, 2);

        // Act
        String result = stats.toString();

        // Assert
        assertTrue(result.contains("date=2025-06-15"));
        assertTrue(result.contains("total=10"));
        assertTrue(result.contains("successful=8"));
        assertTrue(result.contains("failed=2"));
    }

    @Test
    void testProcessUserTimeline_WithOnlyStays_ShouldProcess() {
        // Arrange
        MovementTimelineDTO timeline = createMockTimeline(TimelineDataSource.CACHED, 3, 0); // Only stays
        
        when(persistenceService.hasPersistedTimelineForDate(testUserId, startOfDay)).thenReturn(false);
        when(timelineQueryService.getTimeline(testUserId, startOfDay, endOfDay)).thenReturn(timeline);

        // Act
        boolean result = dailyProcessingService.processUserTimeline(testUserId, startOfDay, endOfDay, testDate);

        // Assert
        assertTrue(result, "Should process timeline with only stays");
    }

    @Test
    void testProcessUserTimeline_WithOnlyTrips_ShouldProcess() {
        // Arrange
        MovementTimelineDTO timeline = createMockTimeline(TimelineDataSource.CACHED, 0, 2); // Only trips
        
        when(persistenceService.hasPersistedTimelineForDate(testUserId, startOfDay)).thenReturn(false);
        when(timelineQueryService.getTimeline(testUserId, startOfDay, endOfDay)).thenReturn(timeline);

        // Act
        boolean result = dailyProcessingService.processUserTimeline(testUserId, startOfDay, endOfDay, testDate);

        // Assert
        assertTrue(result, "Should process timeline with only trips");
    }

    /**
     * Helper method to create a mock timeline DTO for testing.
     */
    private MovementTimelineDTO createMockTimeline(TimelineDataSource dataSource, int staysCount, int tripsCount) {
        MovementTimelineDTO timeline = mock(MovementTimelineDTO.class, withSettings().lenient());
        when(timeline.getDataSource()).thenReturn(dataSource);
        when(timeline.getStaysCount()).thenReturn(staysCount);
        when(timeline.getTripsCount()).thenReturn(tripsCount);
        return timeline;
    }

    /**
     * Helper method to create a test user entity.
     */
    private UserEntity createUser(String email) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setActive(true);
        return user;
    }
}