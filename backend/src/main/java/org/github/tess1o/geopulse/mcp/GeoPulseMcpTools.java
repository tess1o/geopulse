package org.github.tess1o.geopulse.mcp;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.model.*;
import org.github.tess1o.geopulse.ai.service.AIFriendLiveTools;
import org.github.tess1o.geopulse.ai.service.AITimelineTools;
import org.github.tess1o.geopulse.ai.service.AIToolException;
import org.github.tess1o.geopulse.ai.service.SimpleAITools;
import org.github.tess1o.geopulse.statistics.model.RoutesStatistics;

import java.util.List;
import java.util.function.Supplier;

@ApplicationScoped
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
public class GeoPulseMcpTools {

    private final AITimelineTools timelineTools;
    private final AIFriendLiveTools friendLiveTools;
    private final SimpleAITools simpleTools;

    public GeoPulseMcpTools(AITimelineTools timelineTools,
                            AIFriendLiveTools friendLiveTools,
                            SimpleAITools simpleTools) {
        this.timelineTools = timelineTools;
        this.friendLiveTools = friendLiveTools;
        this.simpleTools = simpleTools;
    }

    @Tool(description = "Get today's date from the GeoPulse server. Use before resolving relative dates.")
    String getTodayDate() {
        return executeTool(() -> simpleTools.getTodayDate());
    }

    @Tool(description = "Gets complete timeline with all stays and trips in chronological order.")
    AIMovementTimelineDTO queryTimeline(
            @ToolArg(description = "Start date in YYYY-MM-DD format") String startDate,
            @ToolArg(description = "End date in YYYY-MM-DD format") String endDate,
            @ToolArg(description = "SELF or FRIEND. Defaults to SELF.", required = false) String targetScope,
            @ToolArg(description = "Friend email or full name when targetScope is FRIEND.", required = false) String targetUser) {
        return executeTool(() -> timelineTools.queryTimeline(startDate, endDate, targetScope, targetUser));
    }

    @Tool(description = "Lists places stayed at with timestamps. For counts use getStayStats.")
    List<AITimelineStayDTO> getVisitedLocations(
            @ToolArg(description = "Start date in YYYY-MM-DD format") String startDate,
            @ToolArg(description = "End date in YYYY-MM-DD format") String endDate,
            @ToolArg(description = "SELF or FRIEND. Defaults to SELF.", required = false) String targetScope,
            @ToolArg(description = "Friend email or full name when targetScope is FRIEND.", required = false) String targetUser) {
        return executeTool(() -> timelineTools.getVisitedLocations(startDate, endDate, targetScope, targetUser));
    }

    @Tool(description = "Lists individual trips with details. For totals use getTripStats.")
    List<AITimelineTripDTO> getTripMovements(
            @ToolArg(description = "Start date in YYYY-MM-DD format") String startDate,
            @ToolArg(description = "End date in YYYY-MM-DD format") String endDate,
            @ToolArg(description = "SELF or FRIEND. Defaults to SELF.", required = false) String targetScope,
            @ToolArg(description = "Friend email or full name when targetScope is FRIEND.", required = false) String targetUser) {
        return executeTool(() -> timelineTools.getTripMovements(startDate, endDate, targetScope, targetUser));
    }

    @Tool(description = "Calculates aggregated stay statistics grouped by location, city, country, day, week, or month.")
    List<AIStayStatsDTO> getStayStats(
            @ToolArg(description = "Start date in YYYY-MM-DD format") String startDate,
            @ToolArg(description = "End date in YYYY-MM-DD format") String endDate,
            @ToolArg(description = "LOCATION_NAME, CITY, COUNTRY, DAY, WEEK, or MONTH") StayGroupBy groupBy,
            @ToolArg(description = "SELF or FRIEND. Defaults to SELF.", required = false) String targetScope,
            @ToolArg(description = "Friend email or full name when targetScope is FRIEND.", required = false) String targetUser) {
        return executeTool(() -> timelineTools.getStayStats(startDate, endDate, groupBy, targetScope, targetUser));
    }

    @Tool(description = "Calculates aggregated trip statistics grouped by movement type, route endpoint, day, week, or month.")
    List<AITripStatsDTO> getTripStats(
            @ToolArg(description = "Start date in YYYY-MM-DD format") String startDate,
            @ToolArg(description = "End date in YYYY-MM-DD format") String endDate,
            @ToolArg(description = "MOVEMENT_TYPE, ORIGIN_LOCATION_NAME, DESTINATION_LOCATION_NAME, DAY, WEEK, or MONTH") TripGroupBy groupBy,
            @ToolArg(description = "SELF or FRIEND. Defaults to SELF.", required = false) String targetScope,
            @ToolArg(description = "Friend email or full name when targetScope is FRIEND.", required = false) String targetUser) {
        return executeTool(() -> timelineTools.getTripStats(startDate, endDate, groupBy, targetScope, targetUser));
    }

    @Tool(description = "Returns route frequency and diversity statistics for a date range.")
    RoutesStatistics getRoutePatterns(
            @ToolArg(description = "Start date in YYYY-MM-DD format") String startDate,
            @ToolArg(description = "End date in YYYY-MM-DD format") String endDate,
            @ToolArg(description = "SELF or FRIEND. Defaults to SELF.", required = false) String targetScope,
            @ToolArg(description = "Friend email or full name when targetScope is FRIEND.", required = false) String targetUser) {
        return executeTool(() -> timelineTools.getRoutePatterns(startDate, endDate, targetScope, targetUser));
    }

    @Tool(description = "Lists friends who shared timeline access with the authenticated user.")
    List<AITimelineFriendCandidateDTO> listAccessibleTimelineFriends() {
        return executeTool(() -> timelineTools.listAccessibleTimelineFriends());
    }

    @Tool(description = "Lists friends who shared live location access with the authenticated user.")
    List<AIFriendLiveCandidateDTO> listAccessibleLiveFriends() {
        return executeTool(() -> friendLiveTools.listAccessibleLiveFriends());
    }

    @Tool(description = "Gets a friend's live or last-known location. Includes staleness and last-seen metadata.")
    AIFriendLiveLocationDTO getFriendLiveLocation(
            @ToolArg(description = "Friend email or full name. Optional only when exactly one friend shares live location.", required = false) String targetUser) {
        return executeTool(() -> friendLiveTools.getFriendLiveLocation(targetUser));
    }

    private <T> T executeTool(Supplier<T> action) {
        try {
            return action.get();
        } catch (AIToolException e) {
            log.error("Error while executing tool", e);
            throw new ToolCallException(
                    e.getCode() + ": " + e.getMessage()
            );
        }
    }
}
