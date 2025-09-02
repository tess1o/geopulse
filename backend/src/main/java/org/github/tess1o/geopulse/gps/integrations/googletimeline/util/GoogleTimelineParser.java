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

    private GoogleTimelineParser(){
    }
    
    /**
     * Parse geo string from "geo:lat,lng" format to coordinates
     * @param geoStr the geo string
     * @return [latitude, longitude] or null if invalid
     */
    public static double[] parseGeoString(String geoStr) {
        if (geoStr == null || !geoStr.startsWith("geo:")) {
            return null;
        }
        
        try {
            String coords = geoStr.replace("geo:", "");
            String[] parts = coords.split(",");
            if (parts.length == 2) {
                double lat = Double.parseDouble(parts[0]);
                double lng = Double.parseDouble(parts[1]);
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
        
        if (coords == null) {
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
        
        // Use middle time for visit
        Instant midTime = null;
        if (record.getStartTime() != null && record.getEndTime() != null) {
            long startEpoch = record.getStartTime().getEpochSecond();
            long endEpoch = record.getEndTime().getEpochSecond();
            midTime = Instant.ofEpochSecond((startEpoch + endEpoch) / 2);
        }
        
        GoogleTimelineGpsPoint visitPoint = GoogleTimelineGpsPoint.builder()
                .timestamp(midTime)
                .latitude(coords[0])
                .longitude(coords[1])
                .recordType("visit")
                .activityType(placeName)
                .confidence(confidence)
                .velocityMs(0.0) // Stationary
                .recordIndex(recordIndex)
                .build();
        
        gpsPoints.add(visitPoint);
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