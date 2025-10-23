package org.github.tess1o.geopulse.gps.integrations.googletimeline.util;

import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.googletimeline.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Parser utility for Google Timeline data, implementing the same logic as google_timeline_parser.py
 */
@Slf4j
public final class GoogleTimelineParser {

    /**
     * Interval in minutes between interpolated GPS points for visit records
     */
    public static final int VISIT_INTERPOLATION_INTERVAL_MINUTES = 5;

    private GoogleTimelineParser(){
    }

    /**
     * Parse geo string from both old and new formats to coordinates
     * Old format: "geo:lat,lng"
     * New format: "lat°, lng°"
     * @param geoStr the geo string
     * @return [latitude, longitude] or null if invalid
     */
    public static double[] parseGeoString(String geoStr) {
        if (geoStr == null || geoStr.trim().isEmpty()) {
            return null;
        }

        try {
            String coords;

            // Handle old format: "geo:lat,lng"
            if (geoStr.startsWith("geo:")) {
                coords = geoStr.replace("geo:", "");
            }
            // Handle new format: "lat°, lng°"
            else if (geoStr.contains("°")) {
                coords = geoStr.replace("°", "").trim();
            }
            else {
                // Try parsing as-is
                coords = geoStr.trim();
            }

            String[] parts = coords.split(",");
            if (parts.length == 2) {
                double lat = Double.parseDouble(parts[0].trim());
                double lng = Double.parseDouble(parts[1].trim());
                return new double[]{lat, lng};
            }
        } catch (NumberFormatException e) {
            log.debug("Failed to parse geo string: {}", geoStr);
        }

        return null;
    }
    
    /**
     * Extract GPS points from Google Timeline data
     * @param records list of timeline records
     * @param includeVelocity whether to calculate velocity for activities
     * @return list of extracted GPS points
     */
    public static List<GoogleTimelineGpsPoint> extractGpsPoints(List<GoogleTimelineRecord> records, boolean includeVelocity) {
        List<GoogleTimelineGpsPoint> gpsPoints = new ArrayList<>();
        
        for (int i = 0; i < records.size(); i++) {
            GoogleTimelineRecord record = records.get(i);
            GoogleTimelineRecordType recordType = record.getRecordType();

            switch (recordType) {
                case ACTIVITY -> processActivityRecord(record, i, includeVelocity, gpsPoints);
                case VISIT -> processVisitRecord(record, i, gpsPoints);
                case TIMELINE_PATH -> processTimelinePathRecord(record, i, gpsPoints);
                default -> log.debug("Unknown record type at index {}", i);
            }
        }
        
        // Sort by timestamp
        gpsPoints.sort(Comparator.comparing(GoogleTimelineGpsPoint::getTimestamp, 
                Comparator.nullsLast(Instant::compareTo)));
        
        return gpsPoints;
    }
    
    private static void processActivityRecord(GoogleTimelineRecord record, int recordIndex, 
                                            boolean includeVelocity, List<GoogleTimelineGpsPoint> gpsPoints) {
        GoogleTimelineActivity activity = record.getActivity();
        if (activity == null) {
            return;
        }
        
        double[] startCoords = parseGeoString(activity.getStart());
        double[] endCoords = parseGeoString(activity.getEnd());
        
        double distance = 0.0;
        try {
            if (activity.getDistanceMeters() != null) {
                distance = Double.parseDouble(activity.getDistanceMeters());
            }
        } catch (NumberFormatException e) {
            log.debug("Failed to parse distance: {}", activity.getDistanceMeters());
        }
        
        String activityType = "unknown";
        double confidence = 0.0;
        
        if (activity.getTopCandidate() != null) {
            activityType = activity.getTopCandidate().getType() != null ? 
                    activity.getTopCandidate().getType() : "unknown";
            try {
                if (activity.getTopCandidate().getProbability() != null) {
                    confidence = Double.parseDouble(activity.getTopCandidate().getProbability());
                }
            } catch (NumberFormatException e) {
                log.debug("Failed to parse activity confidence: {}", activity.getTopCandidate().getProbability());
            }
        }
        
        // Calculate velocity if requested
        Double velocity = null;
        if (includeVelocity && record.getStartTime() != null && record.getEndTime() != null && distance > 0) {
            long durationSeconds = ChronoUnit.SECONDS.between(record.getStartTime(), record.getEndTime());
            if (durationSeconds > 0) {
                velocity = distance / durationSeconds; // m/s
            }
        }
        
        // Add start point
        if (startCoords != null) {
            GoogleTimelineGpsPoint startPoint = GoogleTimelineGpsPoint.builder()
                    .timestamp(record.getStartTime())
                    .latitude(startCoords[0])
                    .longitude(startCoords[1])
                    .recordType("activity_start")
                    .activityType(activityType)
                    .confidence(confidence)
                    .velocityMs(velocity)
                    .recordIndex(recordIndex)
                    .build();
            gpsPoints.add(startPoint);
        }
        
        // Add end point
        if (endCoords != null) {
            GoogleTimelineGpsPoint endPoint = GoogleTimelineGpsPoint.builder()
                    .timestamp(record.getEndTime())
                    .latitude(endCoords[0])
                    .longitude(endCoords[1])
                    .recordType("activity_end")
                    .activityType(activityType)
                    .confidence(confidence)
                    .velocityMs(velocity)
                    .recordIndex(recordIndex)
                    .build();
            gpsPoints.add(endPoint);
        }
    }
    
    private static void processVisitRecord(GoogleTimelineRecord record, int recordIndex,
                                         List<GoogleTimelineGpsPoint> gpsPoints) {
        GoogleTimelineVisit visit = record.getVisit();
        if (visit == null || visit.getTopCandidate() == null) {
            return;
        }

        GoogleTimelineVisitCandidate topCandidate = visit.getTopCandidate();
        double[] coords = parseGeoString(topCandidate.getPlaceLocation());

        if (coords == null || !record.hasValidTimes()) {
            return;
        }

        String placeName = topCandidate.getSemanticType() != null ?
                topCandidate.getSemanticType() : "unknown";

        double confidence = 0.0;
        try {
            if (visit.getProbability() != null) {
                confidence = Double.parseDouble(visit.getProbability());
            }
        } catch (NumberFormatException e) {
            log.debug("Failed to parse visit confidence: {}", visit.getProbability());
        }

        // Interpolate GPS points at regular intervals during the visit
        // This helps staypoint detection algorithms identify stays
        interpolateVisitPoints(
                record.getStartTime(),
                record.getEndTime(),
                coords[0],
                coords[1],
                placeName,
                confidence,
                recordIndex,
                gpsPoints
        );
    }

    /**
     * Generate interpolated GPS points for a visit at regular intervals.
     * This creates multiple points at the same location during a visit period,
     * which helps staypoint detection algorithms identify stays.
     *
     * @param startTime visit start time
     * @param endTime visit end time
     * @param latitude visit latitude
     * @param longitude visit longitude
     * @param placeName place name/semantic type
     * @param confidence visit confidence
     * @param recordIndex record index
     * @param gpsPoints list to add generated points to
     */
    private static void interpolateVisitPoints(Instant startTime, Instant endTime,
                                              double latitude, double longitude,
                                              String placeName, double confidence,
                                              int recordIndex,
                                              List<GoogleTimelineGpsPoint> gpsPoints) {
        long durationMinutes = ChronoUnit.MINUTES.between(startTime, endTime);

        // For very short visits (less than interval), generate at least start and end points
        if (durationMinutes < VISIT_INTERPOLATION_INTERVAL_MINUTES) {
            // Add start point
            gpsPoints.add(GoogleTimelineGpsPoint.builder()
                    .timestamp(startTime)
                    .latitude(latitude)
                    .longitude(longitude)
                    .recordType("visit")
                    .activityType(placeName)
                    .confidence(confidence)
                    .velocityMs(0.0)
                    .recordIndex(recordIndex)
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
                        .recordIndex(recordIndex)
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
                    .recordIndex(recordIndex)
                    .build());

            currentTime = currentTime.plus(VISIT_INTERPOLATION_INTERVAL_MINUTES, ChronoUnit.MINUTES);
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
                        .recordIndex(recordIndex)
                        .build());
            }
        }
    }
    
    private static void processTimelinePathRecord(GoogleTimelineRecord record, int recordIndex, 
                                                List<GoogleTimelineGpsPoint> gpsPoints) {
        GoogleTimelinePath[] timelinePath = record.getTimelinePath();
        if (timelinePath == null || record.getStartTime() == null) return;
        
        for (GoogleTimelinePath pathPoint : timelinePath) {
            double[] coords = parseGeoString(pathPoint.getPoint());
            if (coords == null) {
                continue;
            }
            
            int offsetMinutes = 0;
            try {
                if (pathPoint.getDurationMinutesOffsetFromStartTime() != null) {
                    offsetMinutes = Integer.parseInt(pathPoint.getDurationMinutesOffsetFromStartTime());
                }
            } catch (NumberFormatException e) {
                log.debug("Failed to parse offset minutes: {}", pathPoint.getDurationMinutesOffsetFromStartTime());
            }
            
            Instant pointTime = record.getStartTime().plus(offsetMinutes, ChronoUnit.MINUTES);
            
            GoogleTimelineGpsPoint timelinePoint = GoogleTimelineGpsPoint.builder()
                    .timestamp(pointTime)
                    .latitude(coords[0])
                    .longitude(coords[1])
                    .recordType("timeline_point")
                    .activityType("movement")
                    .confidence(1.0)
                    .velocityMs(null) // Could calculate between points
                    .recordIndex(recordIndex)
                    .build();
            
            gpsPoints.add(timelinePoint);
        }
    }
}