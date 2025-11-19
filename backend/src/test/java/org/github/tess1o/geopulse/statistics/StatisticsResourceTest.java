package org.github.tess1o.geopulse.statistics;

import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.statistics.model.*;
import org.github.tess1o.geopulse.statistics.resource.StatisticsResource;
import org.github.tess1o.geopulse.statistics.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StatisticsResource.
 * Tests REST endpoint behavior, input validation, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class StatisticsResourceTest {

    @Mock
    private StatisticsService statisticsService;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private StatisticsResource statisticsResource;

    private UUID testUserId;
    private UserStatistics sampleStatistics;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        sampleStatistics = createSampleStatistics();
        
        lenient().when(currentUserService.getCurrentUserId()).thenReturn(testUserId);
    }

    @Test
    void getRangeStatistics_WithValidParameters_ReturnsSuccessResponse() {
        // Given
        String startTime = "2024-01-01T00:00:00Z";
        String endTime = "2024-01-07T23:59:59Z";
        when(statisticsService.getStatistics(eq(testUserId), any(Instant.class), any(Instant.class), eq(ChartGroupMode.DAYS)))
                .thenReturn(sampleStatistics);

        // When
        Response response = statisticsResource.getRangeStatistics(startTime, endTime);

        // Then
        assertEquals(200, response.getStatus());
        assertEquals(sampleStatistics, response.getEntity());
        
        // Verify service was called with correct parameters
        verify(statisticsService).getStatistics(
                eq(testUserId),
                eq(Instant.parse(startTime)),
                eq(Instant.parse(endTime)),
                eq(ChartGroupMode.DAYS)
        );
    }

    @Test
    void getRangeStatistics_WithNullStartTime_UsesEpoch() {
        // Given
        String endTime = "2024-01-07T23:59:59Z";
        when(statisticsService.getStatistics(any(), any(), any(), any()))
                .thenReturn(sampleStatistics);

        // When
        Response response = statisticsResource.getRangeStatistics(null, endTime);

        // Then
        assertEquals(200, response.getStatus());
        
        // Verify service was called with Epoch as start time
        verify(statisticsService).getStatistics(
                eq(testUserId),
                eq(Instant.EPOCH),
                eq(Instant.parse(endTime)),
                eq(ChartGroupMode.WEEKS) // Should be WEEKS for long range
        );
    }

    @Test
    void getRangeStatistics_WithNullEndTime_UsesCurrentTime() {
        // Given
        String startTime = "2024-01-01T00:00:00Z";
        when(statisticsService.getStatistics(any(), any(), any(), any()))
                .thenReturn(sampleStatistics);

        // When
        Response response = statisticsResource.getRangeStatistics(startTime, null);

        // Then
        assertEquals(200, response.getStatus());
        
        // Verify service was called with current time as end time
        verify(statisticsService).getStatistics(
                eq(testUserId),
                eq(Instant.parse(startTime)),
                any(Instant.class), // Should be close to Instant.now()
                eq(ChartGroupMode.WEEKS) // Should be WEEKS for long range
        );
    }

    @Test
    void getRangeStatistics_WithShortRange_UsesDaysGrouping() {
        // Given - 5 day range (< 10 days)
        String startTime = "2024-01-01T00:00:00Z";
        String endTime = "2024-01-06T00:00:00Z";
        when(statisticsService.getStatistics(any(), any(), any(), eq(ChartGroupMode.DAYS)))
                .thenReturn(sampleStatistics);

        // When
        Response response = statisticsResource.getRangeStatistics(startTime, endTime);

        // Then
        assertEquals(200, response.getStatus());
        verify(statisticsService).getStatistics(any(), any(), any(), eq(ChartGroupMode.DAYS));
    }

    @Test
    void getRangeStatistics_WithLongRange_UsesWeeksGrouping() {
        // Given - 15 day range (> 10 days)
        String startTime = "2024-01-01T00:00:00Z";
        String endTime = "2024-01-16T00:00:00Z";
        when(statisticsService.getStatistics(any(), any(), any(), eq(ChartGroupMode.WEEKS)))
                .thenReturn(sampleStatistics);

        // When
        Response response = statisticsResource.getRangeStatistics(startTime, endTime);

        // Then
        assertEquals(200, response.getStatus());
        verify(statisticsService).getStatistics(any(), any(), any(), eq(ChartGroupMode.WEEKS));
    }

    @Test
    void getRangeStatistics_WithInvalidDateFormat_ThrowsException() {
        // Given
        String invalidStartTime = "invalid-date-format";
        String endTime = "2024-01-07T23:59:59Z";

        // When & Then
        assertThrows(Exception.class, () -> {
            statisticsResource.getRangeStatistics(invalidStartTime, endTime);
        });
        
        // Verify service was never called
        verify(statisticsService, never()).getStatistics(any(), any(), any(), any());
    }

    @Test
    void getWeeklyStatistics_ReturnsCorrectResponse() {
        // Given
        when(statisticsService.getStatistics(any(), any(), any(), eq(ChartGroupMode.DAYS)))
                .thenReturn(sampleStatistics);

        // When
        Response response = statisticsResource.getWeeklyStatistics();

        // Then
        assertEquals(200, response.getStatus());
        assertEquals(sampleStatistics, response.getEntity());
        
        // Verify service was called with correct grouping
        verify(statisticsService).getStatistics(
                eq(testUserId),
                any(Instant.class), // Start time should be 7 days ago
                any(Instant.class), // End time should be end of today
                eq(ChartGroupMode.DAYS)
        );
    }

    @Test
    void getWeeklyStatistics_CalculatesCorrectDateRange() {
        // Given
        when(statisticsService.getStatistics(any(), any(), any(), any()))
                .thenReturn(sampleStatistics);

        // When
        Instant beforeCall = Instant.now();
        Response response = statisticsResource.getWeeklyStatistics();
        Instant afterCall = Instant.now();

        // Then
        assertEquals(200, response.getStatus());
        
        // Verify the date range calculation (capture arguments)
        verify(statisticsService).getStatistics(
                eq(testUserId),
                argThat(start -> {
                    // Start should be approximately 7 days ago
                    Instant expected7DaysAgo = beforeCall.minus(java.time.Duration.ofDays(7))
                            .truncatedTo(java.time.temporal.ChronoUnit.DAYS);
                    Instant latest7DaysAgo = afterCall.minus(java.time.Duration.ofDays(7))
                            .truncatedTo(java.time.temporal.ChronoUnit.DAYS);
                    
                    return start.equals(expected7DaysAgo) || start.equals(latest7DaysAgo);
                }),
                argThat(end -> {
                    // End should be end of today
                    Instant expectedEndOfDay = beforeCall.truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                            .plus(java.time.Duration.ofDays(1))
                            .minusSeconds(1);
                    Instant latestEndOfDay = afterCall.truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                            .plus(java.time.Duration.ofDays(1))
                            .minusSeconds(1);
                    
                    return end.equals(expectedEndOfDay) || end.equals(latestEndOfDay);
                }),
                eq(ChartGroupMode.DAYS)
        );
    }

    @Test
    void getMonthlyStatistics_ReturnsCorrectResponse() {
        // Given
        when(statisticsService.getStatistics(any(), any(), any(), eq(ChartGroupMode.WEEKS)))
                .thenReturn(sampleStatistics);

        // When
        Response response = statisticsResource.getMonthlyStatistics();

        // Then
        assertEquals(200, response.getStatus());
        assertEquals(sampleStatistics, response.getEntity());
        
        // Verify service was called with correct grouping
        verify(statisticsService).getStatistics(
                eq(testUserId),
                any(Instant.class), // Start time should be 30 days ago
                any(Instant.class), // End time should be end of today
                eq(ChartGroupMode.WEEKS)
        );
    }

    @Test
    void getMonthlyStatistics_CalculatesCorrectDateRange() {
        // Given
        when(statisticsService.getStatistics(any(), any(), any(), any()))
                .thenReturn(sampleStatistics);

        // When
        Instant beforeCall = Instant.now();
        Response response = statisticsResource.getMonthlyStatistics();
        Instant afterCall = Instant.now();

        // Then
        assertEquals(200, response.getStatus());
        
        // Verify the date range calculation
        verify(statisticsService).getStatistics(
                eq(testUserId),
                argThat(start -> {
                    // Start should be approximately 30 days ago
                    Instant expected30DaysAgo = beforeCall.minus(java.time.Duration.ofDays(30))
                            .truncatedTo(java.time.temporal.ChronoUnit.DAYS);
                    Instant latest30DaysAgo = afterCall.minus(java.time.Duration.ofDays(30))
                            .truncatedTo(java.time.temporal.ChronoUnit.DAYS);
                    
                    return start.equals(expected30DaysAgo) || start.equals(latest30DaysAgo);
                }),
                argThat(end -> {
                    // End should be end of today
                    Instant expectedEndOfDay = beforeCall.truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                            .plus(java.time.Duration.ofDays(1))
                            .minusSeconds(1);
                    Instant latestEndOfDay = afterCall.truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                            .plus(java.time.Duration.ofDays(1))
                            .minusSeconds(1);
                    
                    return end.equals(expectedEndOfDay) || end.equals(latestEndOfDay);
                }),
                eq(ChartGroupMode.WEEKS)
        );
    }

    @Test
    void getRangeStatistics_WithServiceException_PropagatesException() {
        // Given
        String startTime = "2024-01-01T00:00:00Z";
        String endTime = "2024-01-07T23:59:59Z";
        RuntimeException serviceException = new RuntimeException("Service error");
        when(statisticsService.getStatistics(any(), any(), any(), any()))
                .thenThrow(serviceException);

        // When & Then
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            statisticsResource.getRangeStatistics(startTime, endTime);
        });
        
        assertEquals("Service error", thrown.getMessage());
    }

    @Test
    void getRangeStatistics_WithAuthenticationException_PropagatesException() {
        // Given
        String startTime = "2024-01-01T00:00:00Z";
        String endTime = "2024-01-07T23:59:59Z";
        SecurityException authException = new SecurityException("User not authenticated");
        when(currentUserService.getCurrentUserId()).thenThrow(authException);

        // When & Then
        SecurityException thrown = assertThrows(SecurityException.class, () -> {
            statisticsResource.getRangeStatistics(startTime, endTime);
        });
        
        assertEquals("User not authenticated", thrown.getMessage());
        verify(statisticsService, never()).getStatistics(any(), any(), any(), any());
    }

    @Test
    void allEndpoints_CallCurrentUserService() {
        // Given
        when(statisticsService.getStatistics(any(), any(), any(), any()))
                .thenReturn(sampleStatistics);

        // When
        statisticsResource.getRangeStatistics("2024-01-01T00:00:00Z", "2024-01-07T23:59:59Z");
        statisticsResource.getWeeklyStatistics();
        statisticsResource.getMonthlyStatistics();

        // Then
        verify(currentUserService, times(3)).getCurrentUserId();
    }

    @Test
    void getRangeStatistics_WithExactly10DayRange_UsesWeeksGrouping() {
        // Given - exactly 10 day range (boundary condition)
        String startTime = "2024-01-01T00:00:00Z";
        String endTime = "2024-01-11T00:00:00Z";
        when(statisticsService.getStatistics(any(), any(), any(), eq(ChartGroupMode.WEEKS)))
                .thenReturn(sampleStatistics);

        // When
        Response response = statisticsResource.getRangeStatistics(startTime, endTime);

        // Then
        assertEquals(200, response.getStatus());
        verify(statisticsService).getStatistics(any(), any(), any(), eq(ChartGroupMode.WEEKS));
    }

    @Test
    void getRangeStatistics_WithJustUnder10DayRange_UsesDaysGrouping() {
        // Given - 9 day, 23 hour, 59 minute range (just under 10 days)
        String startTime = "2024-01-01T00:00:00Z";
        String endTime = "2024-01-10T23:59:59Z";
        when(statisticsService.getStatistics(any(), any(), any(), eq(ChartGroupMode.DAYS)))
                .thenReturn(sampleStatistics);

        // When
        Response response = statisticsResource.getRangeStatistics(startTime, endTime);

        // Then
        assertEquals(200, response.getStatus());
        verify(statisticsService).getStatistics(any(), any(), any(), eq(ChartGroupMode.DAYS));
    }

    @Test
    void testUnusedMethod_isNowOutsideRange() {
        // This tests the unused private method for completeness
        // Note: This would require making the method package-private or using reflection
        // For now, we just document that this method exists but is unused
        
        // The method isNowOutsideRange(Instant start, Instant end) exists but is never called
        // This suggests it was intended for caching logic but never implemented
        // This is a code quality issue that should be addressed in refactoring
    }

    // Helper method to create sample statistics
    private UserStatistics createSampleStatistics() {
        return UserStatistics.builder()
                .totalDistanceMeters(25.5)
                .timeMoving(180)
                .dailyAverageDistanceMeters(8.5)
                .uniqueLocationsCount(5)
                .averageSpeed(8.5)
                .mostActiveDay(MostActiveDayDto.builder()
                        .date("01/15")
                        .day("Monday")
                        .distanceTraveled(15.0)
                        .travelTime(90.0)
                        .locationsVisited(3)
                        .build())
                .places(List.of(
                        TopPlace.builder()
                                .name("Home")
                                .visits(5)
                                .duration(2400)
                                .coordinates(new double[]{40.7128, -74.0060})
                                .build(),
                        TopPlace.builder()
                                .name("Work")
                                .visits(4)
                                .duration(1680)
                                .coordinates(new double[]{40.7580, -73.9855})
                                .build()
                ))
                .routes(RoutesStatistics.builder()
                        .uniqueRoutesCount(3)
                        .avgTripDurationSeconds(45.0)
                        .longestTripDurationSeconds(120.0)
                        .longestTripDistanceMeters(25.0)
                        .mostCommonRoute(new MostCommonRoute("Home -> Work", 4))
                        .build())
                .distanceChartsByTripType(createSampleChartsByTripType())
                .build();
    }

    private Map<String, BarChartData> createSampleChartsByTripType() {
        Map<String, BarChartData> charts = new HashMap<>();
        charts.put("CAR", new BarChartData(
                new String[]{"MON", "TUE", "WED", "THU", "FRI"},
                new double[]{5.2, 8.1, 3.4, 6.7, 2.1}
        ));
        charts.put("WALK", new BarChartData(
                new String[]{"MON", "TUE", "WED", "THU", "FRI"},
                new double[]{1.2, 1.5, 1.1, 1.8, 0.9}
        ));
        charts.put("BICYCLE", new BarChartData(
                new String[]{"SAT", "SUN"},
                new double[]{3.5, 4.2}
        ));
        return charts;
    }
}