package org.github.tess1o.geopulse.timeline.service;

import io.quarkus.test.common.QuarkusTestResource;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.timeline.events.TimelinePreferencesUpdatedEvent;
import org.github.tess1o.geopulse.timeline.model.LocationSource;
import org.github.tess1o.geopulse.timeline.model.TimelineStayEntity;
import org.github.tess1o.geopulse.timeline.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Unit tests for TimelineEventService, specifically focusing on the timeline preferences
 * update event handling functionality.
 */
@ExtendWith(MockitoExtension.class)
class TimelineEventServiceTest {

    @Mock
    private TimelineStayRepository stayRepository;

    @Mock
    private TimelineInvalidationService timelineInvalidationService;

    @Mock
    private FavoriteDeletionHandler favoriteDeletionHandler;

    @InjectMocks
    private TimelineEventService timelineEventService;

    private UUID testUserId;
    private TimelinePreferences testPreferences;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testPreferences = new TimelinePreferences();
        testPreferences.setStaypointDetectionAlgorithm("enhanced");
        testPreferences.setTripMinDistanceMeters(200);
    }

    @Test
    void testOnTimelinePreferencesUpdated_WithExistingStays_ShouldInvalidateAll() {
        // Arrange
        List<TimelineStayEntity> mockStays = createMockTimelineStays(3);
        when(stayRepository.findByUser(testUserId)).thenReturn(mockStays);

        TimelinePreferencesUpdatedEvent event = new TimelinePreferencesUpdatedEvent(
            testUserId, 
            testPreferences, 
            false
        );

        // Act
        timelineEventService.onTimelinePreferencesUpdated(event);

        // Assert
        verify(stayRepository).findByUser(testUserId);
        verify(timelineInvalidationService).markStaleAndQueue(mockStays);
        verifyNoMoreInteractions(timelineInvalidationService);
    }

    @Test
    void testOnTimelinePreferencesUpdated_WithNoStays_ShouldNotCallInvalidation() {
        // Arrange
        when(stayRepository.findByUser(testUserId)).thenReturn(Collections.emptyList());

        TimelinePreferencesUpdatedEvent event = new TimelinePreferencesUpdatedEvent(
            testUserId, 
            testPreferences, 
            false
        );

        // Act
        timelineEventService.onTimelinePreferencesUpdated(event);

        // Assert
        verify(stayRepository).findByUser(testUserId);
        verify(timelineInvalidationService, never()).markStaleAndQueue(any());
    }

    @Test
    void testOnTimelinePreferencesUpdated_ResetToDefaults_ShouldStillInvalidate() {
        // Arrange
        List<TimelineStayEntity> mockStays = createMockTimelineStays(5);
        when(stayRepository.findByUser(testUserId)).thenReturn(mockStays);

        TimelinePreferencesUpdatedEvent event = new TimelinePreferencesUpdatedEvent(
            testUserId, 
            null, // null indicates reset to defaults
            true  // wasResetToDefaults = true
        );

        // Act
        timelineEventService.onTimelinePreferencesUpdated(event);

        // Assert
        verify(stayRepository).findByUser(testUserId);
        verify(timelineInvalidationService).markStaleAndQueue(mockStays);
    }

    @Test
    void testOnTimelinePreferencesUpdated_WithLargeNumberOfStays_ShouldHandleAll() {
        // Arrange
        List<TimelineStayEntity> mockStays = createMockTimelineStays(1000); // Large number
        when(stayRepository.findByUser(testUserId)).thenReturn(mockStays);

        TimelinePreferencesUpdatedEvent event = new TimelinePreferencesUpdatedEvent(
            testUserId, 
            testPreferences, 
            false
        );

        // Act
        timelineEventService.onTimelinePreferencesUpdated(event);

        // Assert
        verify(stayRepository).findByUser(testUserId);
        verify(timelineInvalidationService).markStaleAndQueue(mockStays);
    }

    @Test
    void testOnTimelinePreferencesUpdated_RepositoryThrowsException_ShouldHandleGracefully() {
        // Arrange
        when(stayRepository.findByUser(testUserId))
            .thenThrow(new RuntimeException("Database connection error"));

        TimelinePreferencesUpdatedEvent event = new TimelinePreferencesUpdatedEvent(
            testUserId, 
            testPreferences, 
            false
        );

        // Act & Assert - should not throw exception
        timelineEventService.onTimelinePreferencesUpdated(event);

        // Verify repository was called but invalidation service was not
        verify(stayRepository).findByUser(testUserId);
        verify(timelineInvalidationService, never()).markStaleAndQueue(any());
    }

    @Test
    void testOnTimelinePreferencesUpdated_InvalidationServiceThrowsException_ShouldHandleGracefully() {
        // Arrange
        List<TimelineStayEntity> mockStays = createMockTimelineStays(2);
        when(stayRepository.findByUser(testUserId)).thenReturn(mockStays);
        doThrow(new RuntimeException("Invalidation queue error"))
            .when(timelineInvalidationService).markStaleAndQueue(mockStays);

        TimelinePreferencesUpdatedEvent event = new TimelinePreferencesUpdatedEvent(
            testUserId, 
            testPreferences, 
            false
        );

        // Act & Assert - should not throw exception
        timelineEventService.onTimelinePreferencesUpdated(event);

        // Verify both calls were made despite the exception
        verify(stayRepository).findByUser(testUserId);
        verify(timelineInvalidationService).markStaleAndQueue(mockStays);
    }

    /**
     * Helper method to create mock timeline stay entities for testing.
     */
    private List<TimelineStayEntity> createMockTimelineStays(int count) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> {
                TimelineStayEntity stay = new TimelineStayEntity();
                stay.setId((long) (i + 1));
                
                UserEntity user = new UserEntity();
                user.setId(testUserId);
                stay.setUser(user);
                
                stay.setTimestamp(Instant.now().minusSeconds(i * 3600)); // Different timestamps
                stay.setLatitude(52.520008 + (i * 0.001)); // Slightly different locations
                stay.setLongitude(13.404954 + (i * 0.001));
                stay.setLocationName("Test Location " + (i + 1));
                stay.setStayDuration(60);
                stay.setLocationSource(LocationSource.GEOCODING);
                stay.setIsStale(false);
                stay.setTimelineVersion("test-version-" + i);
                stay.setLastUpdated(Instant.now());
                
                return stay;
            })
            .toList();
    }
}