package org.github.tess1o.geopulse.streaming.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * REST API resource for the new streaming timeline algorithm.
 * Provides parallel endpoints to the existing timeline API for easy frontend switching
 * while maintaining identical request/response formats.
 */
@Path("/api/streaming-timeline")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@RequestScoped
public class StreamingTimelineResource {

    @Inject
    StreamingTimelineAggregator streamingTimelineAggregator;

    @Inject
    StreamingTimelineGenerationService streamingTimelineGenerationService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    TimelineConfigurationProvider configurationProvider;

    @GET
    @RolesAllowed("USER")
    public Response getTimeline(@QueryParam("startTime") String startTime, @QueryParam("endTime") String endTime) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Streaming timeline request from user {} for period {} to {}", userId, startTime, endTime);

        try {
            // Parse the time parameters
            Instant start = startTime != null ? Instant.parse(startTime) : Instant.EPOCH;
            Instant end = endTime != null ? Instant.parse(endTime) : Instant.now();

            // Validate time range
            if (start.isAfter(end)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("Start time must be before end time"))
                        .build();
            }

            MovementTimelineDTO timeline = streamingTimelineAggregator.getTimelineFromDb(userId, start, end);

            log.info("Streaming timeline generated for user {}: {} stays, {} trips, {} gaps",
                    userId, timeline.getStaysCount(), timeline.getTripsCount(), timeline.getDataGapsCount());

            return Response.ok(ApiResponse.success(timeline)).build();

        } catch (DateTimeParseException e) {
            log.warn("Invalid time format in request from user {}: {}", userId, e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid time format. Use ISO-8601 format (e.g., 2023-01-01T00:00:00Z)"))
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate streaming timeline for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to generate timeline: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get the user's current timeline preferences/configuration.
     * This mirrors the existing preferences endpoint for consistency.
     *
     * @return The user's effective timeline configuration
     */
    @GET
    @Path("/user/preferences")
    @RolesAllowed("USER")
    public Response getUserPreferences() {
        UUID userId = currentUserService.getCurrentUserId();

        try {
            TimelineConfig config = configurationProvider.getConfigurationForUser(userId);
            return Response.ok(config).build();

        } catch (Exception e) {
            log.error("Failed to get timeline preferences for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get preferences: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Regenerate the complete timeline for the current user using streaming algorithm.
     * This operation will clear all existing timeline data and regenerate it from scratch
     * based on all available GPS data and current timeline preferences.
     *
     * @return Success message if regeneration completed successfully
     */
    @POST
    @Path("/regenerate-all")
    @RolesAllowed("USER")
    public Response regenerateAllTimeline() {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Full streaming timeline regeneration requested by user {}", userId);

        try {
            boolean success = streamingTimelineGenerationService.regenerateFullTimeline(userId);

            if (success) {
                return Response.ok(ApiResponse.success("Streaming timeline regeneration completed successfully")).build();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ApiResponse.error("Streaming timeline regeneration failed. Please try again or contact support."))
                        .build();
            }

        } catch (Exception e) {
            log.error("Failed to regenerate full streaming timeline for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Streaming timeline regeneration failed: " + e.getMessage()))
                    .build();
        }
    }
}