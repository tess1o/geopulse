package org.github.tess1o.geopulse.mcp;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.ai.model.*;
import org.github.tess1o.geopulse.ai.service.AIFriendLiveTools;
import org.github.tess1o.geopulse.ai.service.AITimelineTools;
import org.github.tess1o.geopulse.ai.service.SimpleAITools;
import org.github.tess1o.geopulse.statistics.model.RoutesStatistics;

import java.util.List;

@ApplicationScoped
@RolesAllowed({"USER", "ADMIN"})
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

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Get today's date from the GeoPulse server. Use before resolving relative dates.")
    String getTodayDate() {
        return McpToolCalls.execute(() -> simpleTools.getTodayDate());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Gets complete timeline with all stays and trips in chronological order. Stay and trip ids can be passed to explainTimelineEvent.")
    AIMovementTimelineDTO queryTimeline(
            @ToolArg(description = "Start date in YYYY-MM-DD format") String startDate,
            @ToolArg(description = "End date in YYYY-MM-DD format") String endDate,
            @ToolArg(description = "SELF or FRIEND. Defaults to SELF.", required = false) String targetScope,
            @ToolArg(description = "Friend email or full name when targetScope is FRIEND.", required = false) String targetUser) {
        return McpToolCalls.execute(() -> timelineTools.queryTimeline(startDate, endDate, targetScope, targetUser));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Lists places stayed at with timestamps. For counts use getStayStats.")
    List<AITimelineStayDTO> getVisitedLocations(
            @ToolArg(description = "Start date in YYYY-MM-DD format") String startDate,
            @ToolArg(description = "End date in YYYY-MM-DD format") String endDate,
            @ToolArg(description = "SELF or FRIEND. Defaults to SELF.", required = false) String targetScope,
            @ToolArg(description = "Friend email or full name when targetScope is FRIEND.", required = false) String targetUser) {
        return McpToolCalls.execute(() -> timelineTools.getVisitedLocations(startDate, endDate, targetScope, targetUser));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Lists individual trips with details. For totals use getTripStats.")
    List<AITimelineTripDTO> getTripMovements(
            @ToolArg(description = "Start date in YYYY-MM-DD format") String startDate,
            @ToolArg(description = "End date in YYYY-MM-DD format") String endDate,
            @ToolArg(description = "SELF or FRIEND. Defaults to SELF.", required = false) String targetScope,
            @ToolArg(description = "Friend email or full name when targetScope is FRIEND.", required = false) String targetUser) {
        return McpToolCalls.execute(() -> timelineTools.getTripMovements(startDate, endDate, targetScope, targetUser));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Calculates aggregated stay statistics grouped by location, city, country, day, week, or month.")
    List<AIStayStatsDTO> getStayStats(
            @ToolArg(description = "Start date in YYYY-MM-DD format") String startDate,
            @ToolArg(description = "End date in YYYY-MM-DD format") String endDate,
            @ToolArg(description = "LOCATION_NAME, CITY, COUNTRY, DAY, WEEK, or MONTH") StayGroupBy groupBy,
            @ToolArg(description = "SELF or FRIEND. Defaults to SELF.", required = false) String targetScope,
            @ToolArg(description = "Friend email or full name when targetScope is FRIEND.", required = false) String targetUser) {
        return McpToolCalls.execute(() -> timelineTools.getStayStats(startDate, endDate, groupBy, targetScope, targetUser));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Calculates aggregated trip statistics grouped by movement type, route endpoint, day, week, or month.")
    List<AITripStatsDTO> getTripStats(
            @ToolArg(description = "Start date in YYYY-MM-DD format") String startDate,
            @ToolArg(description = "End date in YYYY-MM-DD format") String endDate,
            @ToolArg(description = "MOVEMENT_TYPE, ORIGIN_LOCATION_NAME, DESTINATION_LOCATION_NAME, DAY, WEEK, or MONTH") TripGroupBy groupBy,
            @ToolArg(description = "SELF or FRIEND. Defaults to SELF.", required = false) String targetScope,
            @ToolArg(description = "Friend email or full name when targetScope is FRIEND.", required = false) String targetUser) {
        return McpToolCalls.execute(() -> timelineTools.getTripStats(startDate, endDate, groupBy, targetScope, targetUser));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Returns route frequency and diversity statistics for a date range.")
    RoutesStatistics getRoutePatterns(
            @ToolArg(description = "Start date in YYYY-MM-DD format") String startDate,
            @ToolArg(description = "End date in YYYY-MM-DD format") String endDate,
            @ToolArg(description = "SELF or FRIEND. Defaults to SELF.", required = false) String targetScope,
            @ToolArg(description = "Friend email or full name when targetScope is FRIEND.", required = false) String targetUser) {
        return McpToolCalls.execute(() -> timelineTools.getRoutePatterns(startDate, endDate, targetScope, targetUser));
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Lists friends who shared timeline access with the authenticated user.")
    List<AITimelineFriendCandidateDTO> listAccessibleTimelineFriends() {
        return McpToolCalls.execute(() -> timelineTools.listAccessibleTimelineFriends());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Lists friends who shared live location access with the authenticated user.")
    List<AIFriendLiveCandidateDTO> listAccessibleLiveFriends() {
        return McpToolCalls.execute(() -> friendLiveTools.listAccessibleLiveFriends());
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "Gets a friend's live or last-known location. Includes staleness and last-seen metadata.")
    AIFriendLiveLocationDTO getFriendLiveLocation(
            @ToolArg(description = "Friend email or full name. Optional only when exactly one friend shares live location.", required = false) String targetUser) {
        return McpToolCalls.execute(() -> friendLiveTools.getFriendLiveLocation(targetUser));
    }
}
