package org.github.tess1o.geopulse.timeline.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.timeline.model.MovementTimelineDTO;
import org.github.tess1o.geopulse.timeline.assembly.TimelineService;
import org.github.tess1o.geopulse.timeline.service.TimelineBackgroundService;
import org.github.tess1o.geopulse.timeline.service.redesign.TimelineRequestRouter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Path("/api/timeline")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@RequestScoped
public class TimelineResource {

    private final TimelineService timelineService;
    private final TimelineBackgroundService backgroundService;
    private final CurrentUserService currentUserService;
    private final TimelineRequestRouter timelineRequestRouter;

    @Inject
    public TimelineResource(TimelineService timelineService, TimelineBackgroundService backgroundService, 
                          CurrentUserService currentUserService, TimelineRequestRouter timelineRequestRouter) {
        this.timelineService = timelineService;
        this.backgroundService = backgroundService;
        this.currentUserService = currentUserService;
        this.timelineRequestRouter = timelineRequestRouter;
    }

    /**
     * Get a movement timeline for a user within a specified time period.
     * This endpoint requires authentication.
     * 
     * Uses the new clean timeline architecture with proper request classification,
     * boundary expansion, previous context prepending, and cross-day gap detection.
     *
     * @param startTime       The start of the time period (ISO-8601 format)
     * @param endTime         The end of the time period (ISO-8601 format)
     * @return The movement timeline
     */
    @GET
    @RolesAllowed("USER")
    public Response getMovementTimeline(
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime) {

        UUID userId = currentUserService.getCurrentUserId();
        try {
            // Parse the time parameters
            Instant start = startTime != null ? Instant.parse(startTime) : Instant.EPOCH;
            Instant end = endTime != null ? Instant.parse(endTime) : Instant.now();

            MovementTimelineDTO timeline = getTimelineWithOptimalStrategy(userId, start, end);
            return Response.ok(ApiResponse.success(timeline)).build();
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid time format. Use ISO-8601 format (e.g., 2023-01-01T00:00:00Z)"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to retrieve movement timeline for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve movement timeline: " + e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Get timeline using the new clean architecture.
     * Automatically handles request classification, boundary expansion, and all complex logic.
     */
    private MovementTimelineDTO getTimelineWithOptimalStrategy(UUID userId, Instant start, Instant end) {
        long durationHours = java.time.Duration.between(start, end).toHours();
        log.info("Timeline request - Start: {}, End: {}, Duration: {} hours", start, end, durationHours);

        // Use the new clean architecture - it handles all the complexity internally
        MovementTimelineDTO timeline = timelineRequestRouter.getTimeline(userId, start, end);
        
        log.debug("Timeline result: {} stays, {} trips, {} data gaps, source: {}", 
                 timeline.getStaysCount(), timeline.getTripsCount(), timeline.getDataGapsCount(), 
                 timeline.getDataSource());
        
        return timeline;
    }

    @GET
    @Path("/user/preferences")
    @RolesAllowed("USER")
    public Response getUserPreferences() {
        UUID userId = currentUserService.getCurrentUserId();
        return Response.ok(timelineService.getEffectiveConfigForUser(userId)).build();
    }
    
    /**
     * Get timeline for a specific date with smart caching.
     * Optimized for single-day requests with automatic cache/live selection.
     *
     * @param date The date in YYYY-MM-DD format (defaults to today)
     * @return The movement timeline for that date
     */
/*    @GET
    @Path("/date")
    @RolesAllowed("USER")
    public Response getTimelineByDate(@QueryParam("date") String date) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            LocalDate requestedDate = date != null ? LocalDate.parse(date) : LocalDate.now(ZoneOffset.UTC);
            Instant dateInstant = requestedDate.atStartOfDay(ZoneOffset.UTC).toInstant();

            MovementTimelineDTO timeline = timelineQueryService.getTimeline(userId, dateInstant);
            return Response.ok(ApiResponse.success(timeline)).build();
        } catch (Exception e) {
            log.error("Failed to retrieve timeline for user {} on date {}", userId, date, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve timeline: " + e.getMessage()))
                    .build();
        }
    }
    */
    /**
     * Force regeneration of timeline for a specific date.
     * Useful for debugging or when manual refresh is needed.
     *
     * @param date The date in YYYY-MM-DD format
     * @return The regenerated timeline
     */
    @POST
    @Path("/regenerate")
    @RolesAllowed("USER")
    public Response forceRegenerateTimeline(@QueryParam("date") String date) {
        if (date == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Date parameter is required"))
                    .build();
        }
        
        UUID userId = currentUserService.getCurrentUserId();
        try {
            LocalDate requestedDate = LocalDate.parse(date);
            Instant startOfDay = requestedDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant endOfDay = requestedDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            
            // Use the new simplified system: delete cache and regenerate
            // For now, just return a live timeline (this method might be deprecated)
            MovementTimelineDTO timeline = timelineService.getMovementTimeline(userId, startOfDay, endOfDay);
            return Response.ok(ApiResponse.success(timeline)).build();
        } catch (Exception e) {
            log.error("Failed to regenerate timeline for user {} on date {}", userId, date, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to regenerate timeline: " + e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Get timeline queue monitoring information.
     * Shows background processing statistics for timeline regeneration.
     *
     * @return Queue statistics and processing information
     */
    @GET
    @Path("/queue/status")
    @RolesAllowed("USER")
    public Response getQueueStatus() {
        try {
            var queueStats = backgroundService.getQueueStatus();
            return Response.ok(ApiResponse.success(queueStats)).build();
        } catch (Exception e) {
            log.error("Failed to retrieve queue status", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve queue status: " + e.getMessage()))
                    .build();
        }
    }
}
