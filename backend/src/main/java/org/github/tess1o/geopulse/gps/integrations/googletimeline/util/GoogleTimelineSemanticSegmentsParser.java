package org.github.tess1o.geopulse.gps.integrations.googletimeline.util;

import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.googletimeline.model.GoogleTimelineGpsPoint;
import org.github.tess1o.geopulse.gps.integrations.googletimeline.model.semanticsegments.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Parser for new Google Timeline format (Semantic Segments)
 */
@Slf4j
public final class GoogleTimelineSemanticSegmentsParser {

    private GoogleTimelineSemanticSegmentsParser() {
    }

    /**
     * Extract GPS points from semantic segments format
     *
     * @param root the root object containing semantic segments and raw signals
     * @param includeVelocity whether to calculate velocity for activities
     * @return list of extracted GPS points
     */
    public static List<GoogleTimelineGpsPoint> extractGpsPoints(GoogleTimelineSemanticSegmentsRoot root, boolean includeVelocity) {
        List<GoogleTimelineGpsPoint> gpsPoints = new ArrayList<>();

        // Process semantic segments (timeline paths, visits, activities)
        if (root.getSemanticSegments() != null) {
            for (int i = 0; i < root.getSemanticSegments().size(); i++) {
                GoogleTimelineSemanticSegment segment = root.getSemanticSegments().get(i);
                processSemanticSegment(segment, i, includeVelocity, gpsPoints);
            }
        }

        // Process raw signals (high-quality GPS data)
        if (root.getRawSignals() != null) {
            for (GoogleTimelineRawSignal signal : root.getRawSignals()) {
                if (signal.getPosition() != null) {
                    processRawPosition(signal.getPosition(), gpsPoints);
                }
            }
        }

        // Sort by timestamp
        gpsPoints.sort(Comparator.comparing(GoogleTimelineGpsPoint::getTimestamp,
                Comparator.nullsLast(Instant::compareTo)));

        return gpsPoints;
    }

    private static void processSemanticSegment(GoogleTimelineSemanticSegment segment, int segmentIndex,
                                              boolean includeVelocity, List<GoogleTimelineGpsPoint> gpsPoints) {
        // Process visit
        if (segment.getVisit() != null) {
            processVisit(segment, segmentIndex, gpsPoints);
        }

        // Process activity
        if (segment.getActivity() != null) {
            processActivity(segment, segmentIndex, includeVelocity, gpsPoints);
        }

        // Process timeline path
        if (segment.getTimelinePath() != null && !segment.getTimelinePath().isEmpty()) {
            processTimelinePath(segment, segmentIndex, gpsPoints);
        }
    }

    private static void processVisit(GoogleTimelineSemanticSegment segment, int segmentIndex,
                                    List<GoogleTimelineGpsPoint> gpsPoints) {
        GoogleTimelineSemanticVisit visit = segment.getVisit();
        if (visit.getTopCandidate() == null || visit.getTopCandidate().getPlaceLocation() == null) {
            return;
        }

        String latLng = visit.getTopCandidate().getPlaceLocation().getLatLng();
        double[] coords = GoogleTimelineParser.parseGeoString(latLng);

        if (coords == null || !segment.hasValidTimes()) {
            return;
        }

        String placeName = visit.getTopCandidate().getSemanticType() != null ?
                visit.getTopCandidate().getSemanticType() : "unknown";

        double confidence = visit.getProbability() != null ? visit.getProbability() : 0.0;

        // Interpolate GPS points at regular intervals during the visit
        interpolateVisitPoints(
                segment.getStartTime(),
                segment.getEndTime(),
                coords[0],
                coords[1],
                placeName,
                confidence,
                segmentIndex,
                gpsPoints
        );
    }

    private static void processActivity(GoogleTimelineSemanticSegment segment, int segmentIndex,
                                       boolean includeVelocity, List<GoogleTimelineGpsPoint> gpsPoints) {
        GoogleTimelineSemanticActivity activity = segment.getActivity();
        if (activity == null) {
            return;
        }

        double[] startCoords = GoogleTimelineParser.parseGeoString(activity.getStart());
        double[] endCoords = GoogleTimelineParser.parseGeoString(activity.getEnd());

        double distance = activity.getDistanceMeters() != null ? activity.getDistanceMeters() : 0.0;

        String activityType = "unknown";
        double confidence = 0.0;

        if (activity.getTopCandidate() != null) {
            activityType = activity.getTopCandidate().getType() != null ?
                    activity.getTopCandidate().getType() : "unknown";
            confidence = activity.getTopCandidate().getProbability() != null ?
                    activity.getTopCandidate().getProbability() : 0.0;
        }

        // Calculate velocity if requested
        Double velocity = null;
        if (includeVelocity && segment.getStartTime() != null && segment.getEndTime() != null && distance > 0) {
            long durationSeconds = ChronoUnit.SECONDS.between(segment.getStartTime(), segment.getEndTime());
            if (durationSeconds > 0) {
                velocity = distance / durationSeconds; // m/s
            }
        }

        // Add start point
        if (startCoords != null) {
            gpsPoints.add(GoogleTimelineGpsPoint.builder()
                    .timestamp(segment.getStartTime())
                    .latitude(startCoords[0])
                    .longitude(startCoords[1])
                    .recordType("activity_start")
                    .activityType(activityType)
                    .confidence(confidence)
                    .velocityMs(velocity)
                    .recordIndex(segmentIndex)
                    .build());
        }

        // Add end point
        if (endCoords != null) {
            gpsPoints.add(GoogleTimelineGpsPoint.builder()
                    .timestamp(segment.getEndTime())
                    .latitude(endCoords[0])
                    .longitude(endCoords[1])
                    .recordType("activity_end")
                    .activityType(activityType)
                    .confidence(confidence)
                    .velocityMs(velocity)
                    .recordIndex(segmentIndex)
                    .build());
        }
    }

    private static void processTimelinePath(GoogleTimelineSemanticSegment segment, int segmentIndex,
                                          List<GoogleTimelineGpsPoint> gpsPoints) {
        for (GoogleTimelineSemanticTimelinePath pathPoint : segment.getTimelinePath()) {
            if (pathPoint.getPoint() == null || pathPoint.getTime() == null) {
                continue;
            }

            double[] coords = GoogleTimelineParser.parseGeoString(pathPoint.getPoint());
            if (coords == null) {
                continue;
            }

            gpsPoints.add(GoogleTimelineGpsPoint.builder()
                    .timestamp(pathPoint.getTime())
                    .latitude(coords[0])
                    .longitude(coords[1])
                    .recordType("timeline_point")
                    .activityType("movement")
                    .confidence(1.0)
                    .velocityMs(null)
                    .recordIndex(segmentIndex)
                    .build());
        }
    }

    private static void processRawPosition(GoogleTimelinePosition position, List<GoogleTimelineGpsPoint> gpsPoints) {
        if (position.getLatLng() == null || position.getTimestamp() == null) {
            return;
        }

        double[] coords = GoogleTimelineParser.parseGeoString(position.getLatLng());
        if (coords == null) {
            return;
        }

        String source = position.getSource() != null ? position.getSource() : "unknown";
        Double speedMs = position.getSpeedMetersPerSecond();

        gpsPoints.add(GoogleTimelineGpsPoint.builder()
                .timestamp(position.getTimestamp())
                .latitude(coords[0])
                .longitude(coords[1])
                .recordType("raw_position")
                .activityType(source)
                .confidence(1.0)
                .velocityMs(speedMs)
                .recordIndex(-1) // Raw signals don't have segment index
                .build());
    }

    /**
     * Generate interpolated GPS points for a visit at regular intervals.
     * Uses the same logic as the legacy parser.
     */
    private static void interpolateVisitPoints(Instant startTime, Instant endTime,
                                              double latitude, double longitude,
                                              String placeName, double confidence,
                                              int segmentIndex,
                                              List<GoogleTimelineGpsPoint> gpsPoints) {
        long durationMinutes = ChronoUnit.MINUTES.between(startTime, endTime);

        // For very short visits (less than interval), generate at least start and end points
        if (durationMinutes < GoogleTimelineParser.VISIT_INTERPOLATION_INTERVAL_MINUTES) {
            // Add start point
            gpsPoints.add(GoogleTimelineGpsPoint.builder()
                    .timestamp(startTime)
                    .latitude(latitude)
                    .longitude(longitude)
                    .recordType("visit")
                    .activityType(placeName)
                    .confidence(confidence)
                    .velocityMs(0.0)
                    .recordIndex(segmentIndex)
                    .build());

            // Add end point if it's different from start
            if (durationMinutes > 0) {
                gpsPoints.add(GoogleTimelineGpsPoint.builder()
                        .timestamp(endTime)
                        .latitude(latitude)
                        .longitude(longitude)
                        .recordType("visit")
                        .activityType(placeName)
                        .confidence(confidence)
                        .velocityMs(0.0)
                        .recordIndex(segmentIndex)
                        .build());
            }
            return;
        }

        // Generate points at regular intervals
        Instant currentTime = startTime;
        while (!currentTime.isAfter(endTime)) {
            gpsPoints.add(GoogleTimelineGpsPoint.builder()
                    .timestamp(currentTime)
                    .latitude(latitude)
                    .longitude(longitude)
                    .recordType("visit")
                    .activityType(placeName)
                    .confidence(confidence)
                    .velocityMs(0.0)
                    .recordIndex(segmentIndex)
                    .build());

            currentTime = currentTime.plus(GoogleTimelineParser.VISIT_INTERPOLATION_INTERVAL_MINUTES, ChronoUnit.MINUTES);
        }

        // Add final point at exact end time if we didn't already add it
        if (!gpsPoints.isEmpty()) {
            GoogleTimelineGpsPoint lastPoint = gpsPoints.get(gpsPoints.size() - 1);
            if (!lastPoint.getTimestamp().equals(endTime)) {
                gpsPoints.add(GoogleTimelineGpsPoint.builder()
                        .timestamp(endTime)
                        .latitude(latitude)
                        .longitude(longitude)
                        .recordType("visit")
                        .activityType(placeName)
                        .confidence(confidence)
                        .velocityMs(0.0)
                        .recordIndex(segmentIndex)
                        .build());
            }
        }
    }
}
