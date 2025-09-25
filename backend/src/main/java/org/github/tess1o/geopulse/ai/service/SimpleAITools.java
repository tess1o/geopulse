package org.github.tess1o.geopulse.ai.service;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Simple AI Tools class without CDI proxying to work with LangChain4j reflection
 */
@Slf4j
public class SimpleAITools {
    
    private final StreamingTimelineAggregator streamingTimelineAggregator;
    private final CurrentUserService currentUserService;
    
    public SimpleAITools(StreamingTimelineAggregator streamingTimelineAggregator, 
                        CurrentUserService currentUserService) {
        this.streamingTimelineAggregator = streamingTimelineAggregator;
        this.currentUserService = currentUserService;
    }
    
    @Tool("MANDATORY: Call this first when user mentions relative dates like 'September', 'last month', 'this year' - returns current date with year")
    public String getTodayDate(){
        LocalDate today = LocalDate.now();
        log.info("🔧 AI TOOL EXECUTED: getTodayDate() - returning: {}", today);
        return "Today's date is: " + today.toString() + " (current year is " + today.getYear() + ")";
    }

    @Tool("Query user's timeline data for specific date ranges and analyze stays/trips/data gaps. End date must be after start date at least by 1 day")
    public String queryTimeline(@P("Start date") LocalDate startDate, @P("End date") LocalDate endDate) {
        log.info("🔧 AI TOOL EXECUTED: queryTimeline({}, {})", startDate, endDate);

        if (startDate.equals(endDate)) {
            endDate = endDate.plusDays(1);
        }

        Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        UUID userId = currentUserService.getCurrentUserId();

        MovementTimelineDTO timeline = streamingTimelineAggregator.getTimelineFromDb(userId, start, end);
        
        // Format timeline data in a clean, readable way for AI processing
        StringBuilder result = new StringBuilder();
        result.append("Timeline data for ").append(startDate).append(" to ").append(endDate).append(":\n\n");
        
        if (timeline.getTrips() != null && !timeline.getTrips().isEmpty()) {
            result.append("TRIPS (").append(timeline.getTrips().size()).append("):\n");
            timeline.getTrips().forEach(trip -> {
                String startTime = formatTimestamp(trip.getTimestamp());
                result.append("- Trip at ").append(startTime)
                      .append(": ").append(trip.getMovementType() != null ? trip.getMovementType() : "Unknown type")
                      .append(", Distance: ").append(trip.getDistanceMeters()).append("m")
                      .append(", Duration: ").append(formatDuration(trip.getTripDuration())).append("\n");
            });
            result.append("\n");
        }
        
        if (timeline.getStays() != null && !timeline.getStays().isEmpty()) {
            result.append("STAYS (").append(timeline.getStays().size()).append("):\n");
            timeline.getStays().forEach(stay -> {
                String startTime = formatTimestamp(stay.getTimestamp());
                String duration = formatDuration(stay.getStayDuration());
                result.append("- Stay at ").append(startTime)
                      .append(" at location: ").append(stay.getLocationName() != null ? stay.getLocationName() : "Unknown location")
                      .append(", Duration: ").append(duration).append("\n");
            });
            result.append("\n");
        }
        
        if ((timeline.getTrips() == null || timeline.getTrips().isEmpty()) &&
            (timeline.getStays() == null || timeline.getStays().isEmpty())) {
            result.append("No timeline data found for this period.\n");
        }
        
        return result.toString();
    }
    
    private String formatTimestamp(Instant timestamp) {
        if (timestamp == null) return "Unknown time";
        return timestamp.atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }
    
    private String formatDuration(long durationSeconds) {
        if (durationSeconds < 60) {
            return durationSeconds + " seconds";
        } else if (durationSeconds < 3600) {
            long minutes = durationSeconds / 60;
            return minutes + " minutes";
        } else {
            long hours = durationSeconds / 3600;
            long remainingMinutes = (durationSeconds % 3600) / 60;
            if (remainingMinutes > 0) {
                return hours + " hours " + remainingMinutes + " minutes";
            } else {
                return hours + " hours";
            }
        }
    }
    
    @Tool("Get only the locations/places the user visited (stays) for a specific date range")
    public String getVisitedLocations(@P("Start date") LocalDate startDate, @P("End date") LocalDate endDate) {
        log.info("🔧 AI TOOL EXECUTED: getVisitedLocations({}, {})", startDate, endDate);

        if (startDate.equals(endDate)) {
            endDate = endDate.plusDays(1);
        }

        Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        UUID userId = currentUserService.getCurrentUserId();

        MovementTimelineDTO timeline = streamingTimelineAggregator.getTimelineFromDb(userId, start, end);
        
        StringBuilder result = new StringBuilder();
        result.append("Locations visited from ").append(startDate).append(" to ").append(endDate).append(":\n\n");
        
        if (timeline.getStays() != null && !timeline.getStays().isEmpty()) {
            timeline.getStays().forEach(stay -> {
                String startTime = formatTimestamp(stay.getTimestamp());
                String duration = formatDuration(stay.getStayDuration());
                String locationName = stay.getLocationName() != null ? stay.getLocationName() : "Unknown location";
                result.append("- ").append(locationName)
                      .append(" (arrived: ").append(startTime)
                      .append(", stayed: ").append(duration).append(")\n");
            });
        } else {
            result.append("No locations/stays found for this period.\n");
        }
        
        return result.toString();
    }
    
    @Tool("Get only the trips/movements the user made for a specific date range")
    public String getTripMovements(@P("Start date") LocalDate startDate, @P("End date") LocalDate endDate) {
        log.info("🔧 AI TOOL EXECUTED: getTripMovements({}, {})", startDate, endDate);

        if (startDate.equals(endDate)) {
            endDate = endDate.plusDays(1);
        }

        Instant start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = endDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        UUID userId = currentUserService.getCurrentUserId();

        MovementTimelineDTO timeline = streamingTimelineAggregator.getTimelineFromDb(userId, start, end);
        
        StringBuilder result = new StringBuilder();
        result.append("Trips/movements from ").append(startDate).append(" to ").append(endDate).append(":\n\n");
        
        if (timeline.getTrips() != null && !timeline.getTrips().isEmpty()) {
            timeline.getTrips().forEach(trip -> {
                String startTime = formatTimestamp(trip.getTimestamp());
                String movementType = trip.getMovementType() != null ? trip.getMovementType() : "Unknown type";
                String duration = formatDuration(trip.getTripDuration());
                result.append("- ").append(movementType)
                      .append(" trip at ").append(startTime)
                      .append(" (distance: ").append(trip.getDistanceMeters()).append("m")
                      .append(", duration: ").append(duration).append(")\n");
            });
        } else {
            result.append("No trips/movements found for this period.\n");
        }
        
        return result.toString();
    }
}