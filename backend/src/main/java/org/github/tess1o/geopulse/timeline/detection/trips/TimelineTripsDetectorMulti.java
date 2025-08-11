package org.github.tess1o.geopulse.timeline.detection.trips;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.timeline.model.TimelineConfig;
import org.github.tess1o.geopulse.timeline.model.TimelineStayPoint;
import org.github.tess1o.geopulse.timeline.model.TimelineTrip;
import org.github.tess1o.geopulse.timeline.model.TravelMode;
import org.github.tess1o.geopulse.timeline.core.SpatialCalculationService;
import org.github.tess1o.geopulse.timeline.core.TimelineValidationService;
import org.github.tess1o.geopulse.timeline.core.VelocityAnalysisService;
import org.github.tess1o.geopulse.timeline.util.TimelineConstants;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@Slf4j
public class TimelineTripsDetectorMulti implements TimelineTripsDetector {

    private final TravelClassification travelClassification;
    private final VelocityAnalysisService velocityAnalysisService;
    private final TimelineValidationService validationService;
    private final SpatialCalculationService spatialCalculationService;

    @Inject
    public TimelineTripsDetectorMulti(TravelClassification travelClassification,
                                    VelocityAnalysisService velocityAnalysisService,
                                    TimelineValidationService validationService,
                                    SpatialCalculationService spatialCalculationService) {
        this.travelClassification = travelClassification;
        this.velocityAnalysisService = velocityAnalysisService;
        this.validationService = validationService;
        this.spatialCalculationService = spatialCalculationService;
    }

    public List<TimelineTrip> detectTrips(TimelineConfig config, List<GpsPointPathPointDTO> allPoints, List<TimelineStayPoint> timelineStayPoints) {
        List<TimelineTrip> trips = new ArrayList<>();

        for (int i = 1; i < timelineStayPoints.size(); i++) {
            TimelineStayPoint prevStay = timelineStayPoints.get(i - 1);
            TimelineStayPoint nextStay = timelineStayPoints.get(i);

            Instant tripStartTime = prevStay.endTime();
            Instant tripEndTime = nextStay.startTime();

            // Get all points between the two stay points
            List<GpsPointPathPointDTO> tripPoints = allPoints.stream()
                    .filter(p -> validationService.isBetweenInclusive(tripStartTime, tripEndTime, p.getTimestamp()))
                    .toList();

            if (!tripPoints.isEmpty()) {
                // Skip if stay points are adjacent (no travel time)
                if (!tripStartTime.isBefore(tripEndTime)) {
                    continue;
                }
                
                // Try to detect multiple travel modes within this trip segment
                List<TimelineTrip> segmentTrips = detectMultiModalTrips(tripPoints, tripStartTime, tripEndTime, config);
                trips.addAll(segmentTrips);
            }
        }

        return trips;
    }

    private List<TimelineTrip> detectMultiModalTrips(List<GpsPointPathPointDTO> tripPoints, Instant overallStart, Instant overallEnd, TimelineConfig config) {
        List<TimelineTrip> trips = new ArrayList<>();

        // First, classify the entire segment to see if it's clearly single-mode
        Duration totalDuration = Duration.between(overallStart, overallEnd);
        TravelMode overallMode = travelClassification.classifyTravelType(tripPoints, totalDuration);

        // Try to detect mode changes within the trip
        List<TravelSegment> segments = detectTravelModeSegments(tripPoints);
        segments = mergeSmallSegments(segments, tripPoints.size());

        // Only split into multiple trips if we're confident (95% threshold)
        if (shouldSplitIntoMultipleTrips(segments, tripPoints.size())) {
            // Create separate trips for each confident segment
            for (TravelSegment segment : segments) {
                List<? extends GpsPoint> segmentPoints = tripPoints.subList(segment.startIndex, segment.endIndex + 1);
                Instant segmentStart = segmentPoints.get(0).getTimestamp();
                Instant segmentEnd = segmentPoints.get(segmentPoints.size() - 1).getTimestamp();

                TimelineTrip trip = new TimelineTrip(segmentStart, segmentEnd, segmentPoints, segment.travelMode);
                // Trust stay points - segments between stays are valid
                trips.add(trip);
            }
        } else {
            // Fall back to single trip with overall classification
            TimelineTrip trip = new TimelineTrip(overallStart, overallEnd, tripPoints, overallMode);
            // Trust stay points - movement between stays is valid
            trips.add(trip);
        }

        return trips;
    }

    private List<TravelSegment> detectTravelModeSegments(List<GpsPointPathPointDTO> points) {

        List<GpsPointPathPointDTO> accuratePoints = points.stream()
                .filter(p -> p.getAccuracy() == null || p.getAccuracy() <= TimelineConstants.HIGH_ACCURACY_FILTER_THRESHOLD)
                .toList();

        if (accuratePoints.size() < TimelineConstants.MIN_ACCURATE_POINTS_FOR_ANALYSIS) {
            return new ArrayList<>(); // Not enough accurate data
        }

        List<TravelSegment> segments = new ArrayList<>();

        if (points.size() < TimelineConstants.MIN_POINTS_FOR_RELIABLE_DETECTION) {
            return segments;
        }

        // Analyze velocity patterns in sliding windows
        int windowSize = Math.max(TimelineConstants.MIN_ADAPTIVE_WINDOW_SIZE, 
                                (int) (points.size() / TimelineConstants.ADAPTIVE_WINDOW_DIVISOR));
        List<VelocityAnalysisService.VelocityWindow> windows = new ArrayList<>();

        for (int i = 0; i <= points.size() - windowSize; i++) {
            List<GpsPointPathPointDTO> windowPoints = points.subList(i, i + windowSize);
            VelocityAnalysisService.VelocityWindow window = velocityAnalysisService.analyzeVelocityWindow(windowPoints, i);
            windows.add(window);
        }

        // Group consecutive windows with similar characteristics
        TravelMode currentMode = null;
        int segmentStart = 0;

        for (VelocityAnalysisService.VelocityWindow window : windows) {
            TravelMode windowMode = classifyWindowTravelMode(window);

            if (currentMode == null) {
                currentMode = windowMode;
                segmentStart = window.startIndex();
            } else if (!currentMode.equals(windowMode)) {
                // Mode change detected - end current segment
                segments.add(new TravelSegment(segmentStart, window.startIndex() - 1, currentMode));
                currentMode = windowMode;
                segmentStart = window.startIndex();
            }
        }

        // Add final segment
        if (currentMode != null) {
            segments.add(new TravelSegment(segmentStart, points.size() - 1, currentMode));
        }

        return segments;
    }

    private List<TravelSegment> mergeSmallSegments(List<TravelSegment> segments, int totalPoints) {
        List<TravelSegment> merged = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            TravelSegment segment = segments.get(i);
            int segmentSize = segment.endIndex - segment.startIndex + 1;

            // If segment is small and UNKNOWN, try to merge with adjacent confident segment
            if (segmentSize < totalPoints * TimelineConstants.SMALL_SEGMENT_RATIO && segment.travelMode == TravelMode.UNKNOWN) {
                // Find adjacent confident segment to merge with
                TravelMode mergeMode = null;

                // Check previous segment
                if (i > 0 && segments.get(i - 1).travelMode != TravelMode.UNKNOWN) {
                    mergeMode = segments.get(i - 1).travelMode;
                    // Extend previous segment
                    TravelSegment prev = merged.get(merged.size() - 1);
                    merged.set(merged.size() - 1,
                            new TravelSegment(prev.startIndex, segment.endIndex, prev.travelMode));
                    continue;
                }

                // Check next segment
                if (i < segments.size() - 1 && segments.get(i + 1).travelMode != TravelMode.UNKNOWN) {
                    mergeMode = segments.get(i + 1).travelMode;
                    // Will be merged when processing next segment
                    merged.add(new TravelSegment(segment.startIndex, segment.endIndex, mergeMode));
                    continue;
                }
            }

            merged.add(segment);
        }

        return merged;
    }


    private TravelMode classifyWindowTravelMode(VelocityAnalysisService.VelocityWindow window) {
        // Convert from km/h to reasonable thresholds
        double medianKmh = window.median();
        double p95Kmh = window.p95();
        double maxKmh = window.max();

        // High confidence thresholds
        if (maxKmh > TimelineConstants.DRIVING_MIN_SPEED_KMH && medianKmh > TimelineConstants.DRIVING_MEDIAN_THRESHOLD_KMH) {
            return TravelMode.CAR;
        } else if (maxKmh < TimelineConstants.WALKING_MAX_SPEED_KMH && medianKmh < TimelineConstants.WALKING_MEDIAN_THRESHOLD_KMH) {
            return TravelMode.WALKING;
        }

        // Default to unknown for ambiguous cases
        return TravelMode.UNKNOWN;
    }

    private boolean shouldSplitIntoMultipleTrips(List<TravelSegment> segments, int totalPoints) {
        if (segments.size() < 2) {
            return false; // No mode changes detected
        }

        // Confidence criteria for splitting:
        // 1. Each segment must be substantial 
        // 2. Mode changes must be clear and sustained (not just brief spikes)
        // 3. At least one segment must be high-confidence (CAR, WALK, or BIKE)

        boolean hasSubstantialSegments = segments.stream()
                .allMatch(seg -> (seg.endIndex - seg.startIndex + 1) >= totalPoints * TimelineConstants.SUBSTANTIAL_SEGMENT_RATIO);

        boolean hasConfidentModes = segments.stream()
                .anyMatch(seg -> seg.travelMode != TravelMode.UNKNOWN);

        long distinctModes = segments.stream()
                .map(seg -> seg.travelMode)
                .filter(mode -> mode != TravelMode.UNKNOWN)
                .distinct()
                .count();
        // Require at least 2 distinct confident modes
        return hasSubstantialSegments && hasConfidentModes && distinctModes >= 2;
    }

    // Helper classes
    private record TravelSegment(int startIndex, int endIndex, TravelMode travelMode) {
    }

}