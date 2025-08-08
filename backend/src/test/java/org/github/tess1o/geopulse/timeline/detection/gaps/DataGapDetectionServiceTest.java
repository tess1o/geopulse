package org.github.tess1o.geopulse.timeline.detection.gaps;

import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineDataGapDTO;
import org.github.tess1o.geopulse.timeline.model.TrackPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataGapDetectionServiceTest {

    private DataGapDetectionService service;
    private TimelineConfig config;

    @BeforeEach
    void setUp() {
        service = new DataGapDetectionService();
        config = TimelineConfig.builder()
                .dataGapThresholdSeconds(3600) // 1 hour
                .dataGapMinDurationSeconds(1800) // 30 minutes
                .build();
    }

    @Test
    void testDetectDataGaps_NoGaps() {
        // Arrange - track points with small time intervals
        Instant now = Instant.now();
        List<TrackPoint> trackPoints = List.of(
                createTrackPoint(now),
                createTrackPoint(now.plusSeconds(600)),  // 10 minutes
                createTrackPoint(now.plusSeconds(1200)), // 20 minutes
                createTrackPoint(now.plusSeconds(1800))  // 30 minutes
        );

        // Act
        List<TimelineDataGapDTO> gaps = service.detectDataGaps(config, trackPoints);

        // Assert
        assertTrue(gaps.isEmpty(), "Expected no data gaps with small time intervals");
    }

    @Test
    void testDetectDataGaps_WithGaps() {
        // Arrange - track points with large time intervals
        Instant now = Instant.now();
        List<TrackPoint> trackPoints = List.of(
                createTrackPoint(now),
                createTrackPoint(now.plusSeconds(7200)),  // 2 hours gap
                createTrackPoint(now.plusSeconds(14400)), // Another 2 hours gap
                createTrackPoint(now.plusSeconds(21600))  // Another 2 hours gap
        );

        // Act
        List<TimelineDataGapDTO> gaps = service.detectDataGaps(config, trackPoints);

        // Assert
        assertEquals(3, gaps.size(), "Expected 3 data gaps");
        
        // Check first gap
        TimelineDataGapDTO firstGap = gaps.get(0);
        assertEquals(now, firstGap.getStartTime());
        assertEquals(now.plusSeconds(7200), firstGap.getEndTime());
        assertEquals(7200, firstGap.getDurationSeconds());
    }

    @Test
    void testDetectDataGaps_GapTooShort() {
        // Arrange - gap exceeds threshold but is shorter than minimum duration
        Instant now = Instant.now();
        List<TrackPoint> trackPoints = List.of(
                createTrackPoint(now),
                createTrackPoint(now.plusSeconds(5000))  // ~1.4 hours (exceeds threshold of 1 hour, but less than minimum 1.5 hours)
        );

        // Update config to have higher minimum duration
        TimelineConfig configWithHigherMin = TimelineConfig.builder()
                .dataGapThresholdSeconds(3600) // 1 hour
                .dataGapMinDurationSeconds(5400) // 1.5 hours minimum
                .build();

        // Act
        List<TimelineDataGapDTO> gaps = service.detectDataGaps(configWithHigherMin, trackPoints);

        // Assert
        assertTrue(gaps.isEmpty(), "Expected no gaps when duration is below minimum");
    }

    @Test
    void testDetectDataGaps_NullConfig() {
        // Arrange
        List<TrackPoint> trackPoints = List.of(
                createTrackPoint(Instant.now()),
                createTrackPoint(Instant.now().plusSeconds(7200))
        );

        TimelineConfig nullConfig = TimelineConfig.builder().build();

        // Act
        List<TimelineDataGapDTO> gaps = service.detectDataGaps(nullConfig, trackPoints);

        // Assert
        assertTrue(gaps.isEmpty(), "Expected no gaps when thresholds are null");
    }

    @Test
    void testDetectDataGaps_InsufficientTrackPoints() {
        // Arrange - only one track point
        List<TrackPoint> trackPoints = List.of(createTrackPoint(Instant.now()));

        // Act
        List<TimelineDataGapDTO> gaps = service.detectDataGaps(config, trackPoints);

        // Assert
        assertTrue(gaps.isEmpty(), "Expected no gaps with insufficient track points");
    }

    @Test
    void testHasDataGap_True() {
        // Arrange
        TrackPoint first = createTrackPoint(Instant.now());
        TrackPoint second = createTrackPoint(Instant.now().plusSeconds(7200)); // 2 hours later

        // Act
        boolean hasGap = service.hasDataGap(config, first, second);

        // Assert
        assertTrue(hasGap, "Expected data gap detected");
    }

    @Test
    void testHasDataGap_False() {
        // Arrange
        TrackPoint first = createTrackPoint(Instant.now());
        TrackPoint second = createTrackPoint(Instant.now().plusSeconds(1800)); // 30 minutes later

        // Act
        boolean hasGap = service.hasDataGap(config, first, second);

        // Assert
        assertFalse(hasGap, "Expected no data gap detected");
    }

    @Test
    void testSplitTrackPointsAtGaps() {
        // Arrange - track points with gaps in between
        Instant now = Instant.now();
        List<TrackPoint> trackPoints = List.of(
                createTrackPoint(now),
                createTrackPoint(now.plusSeconds(1800)),    // 30 minutes - no gap
                createTrackPoint(now.plusSeconds(9000)),    // 2.5 hours gap from previous
                createTrackPoint(now.plusSeconds(10800)),   // 30 minutes - no gap
                createTrackPoint(now.plusSeconds(18000))    // 2 hours gap from previous
        );

        // Act
        List<List<TrackPoint>> segments = service.splitTrackPointsAtGaps(config, trackPoints);

        // Assert
        assertEquals(3, segments.size(), "Expected 3 segments after splitting at gaps");
        assertEquals(2, segments.get(0).size(), "First segment should have 2 points");
        assertEquals(2, segments.get(1).size(), "Second segment should have 2 points");
        assertEquals(1, segments.get(2).size(), "Third segment should have 1 point");
    }

    private TrackPoint createTrackPoint(Instant timestamp) {
        return TrackPoint.builder()
                .timestamp(timestamp)
                .latitude(40.7128)
                .longitude(-74.0060)
                .accuracy(10.0)
                .build();
    }
}