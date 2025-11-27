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
import org.github.tess1o.geopulse.streaming.model.TimelineJobProgress;
import org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
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
    CurrentUserService currentUserService;

    @Inject
    TimelineConfigurationProvider configurationProvider;

    @Inject
    TimelineJobProgressService jobProgressService;

    @Inject
    AsyncTimelineGenerationService asyncTimelineGenerationService;

    @Inject
    org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProperties timelineConfigurationProperties;

    @Inject
    org.github.tess1o.geopulse.streaming.service.TripClassificationDetailsService tripClassificationDetailsService;

    @GET
    @RolesAllowed({"USER", "ADMIN"})
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
     * Get timeline item counts for a given time range without fetching full data.
     * This endpoint is used by the frontend to check if the dataset is too large for Timeline page
     * and guide users to Timeline Reports for large datasets.
     *
     * @param startTime Start time in ISO-8601 format
     * @param endTime End time in ISO-8601 format
     * @return Count of stays, trips, data gaps, total items, and the configured limit
     */
    @GET
    @Path("/count")
    @RolesAllowed({"USER", "ADMIN"})
    public Response getTimelineCount(@QueryParam("startTime") String startTime, @QueryParam("endTime") String endTime) {
        UUID userId = currentUserService.getCurrentUserId();
        log.debug("Timeline count request from user {} for period {} to {}", userId, startTime, endTime);

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

            // Get counts from aggregator
            Map<String, Long> counts = streamingTimelineAggregator.getTimelineItemCounts(userId, start, end);

            // Add the configured limit to the response
            counts.put("limit", timelineConfigurationProperties.getViewItemLimit().longValue());

            log.debug("Timeline counts for user {}: {}", userId, counts);

            return Response.ok(ApiResponse.success(counts)).build();

        } catch (DateTimeParseException e) {
            log.warn("Invalid time format in count request from user {}: {}", userId, e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid time format. Use ISO-8601 format (e.g., 2023-01-01T00:00:00Z)"))
                    .build();

        } catch (Exception e) {
            log.error("Failed to get timeline counts for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get timeline counts: " + e.getMessage()))
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
    @RolesAllowed({"USER", "ADMIN"})
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
     * Get detailed classification information for a specific trip.
     * Provides comprehensive explanation of why a trip was classified as a specific transport type,
     * including GPS statistics, configuration thresholds, and step-by-step decision trace.
     *
     * @param tripId ID of the trip to explain
     * @return Trip classification details with explanation
     */
    @GET
    @Path("/trips/{tripId}/classification")
    @RolesAllowed({"USER", "ADMIN"})
    public Response getTripClassificationDetails(@PathParam("tripId") Long tripId) {
        UUID userId = currentUserService.getCurrentUserId();
        log.debug("Trip classification details requested for trip {} by user {}", tripId, userId);

        try {
            var details = tripClassificationDetailsService.getTripClassificationDetails(tripId, userId);

            if (details.isEmpty()) {
                log.warn("Trip {} not found or access denied for user {}", tripId, userId);
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("Trip not found or access denied"))
                        .build();
            }

            return Response.ok(ApiResponse.success(details.get())).build();

        } catch (Exception e) {
            log.error("Failed to get classification details for trip {} and user {}", tripId, userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get classification details: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Regenerate the complete timeline for the current user using streaming algorithm.
     * This operation will clear all existing timeline data and regenerate it from scratch
     * based on all available GPS data and current timeline preferences.
     * <p>
     * The regeneration runs asynchronously, returning a job ID immediately for progress tracking.
     *
     * @return Job ID for tracking progress
     */
    @POST
    @Path("/regenerate-all")
    @RolesAllowed({"USER", "ADMIN"})
    public Response regenerateAllTimeline() {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Full streaming timeline regeneration requested by user {}", userId);

        try {
            // Start the regeneration asynchronously and get job ID immediately
            UUID jobId = asyncTimelineGenerationService.regenerateTimelineAsync(userId);

            log.info("Timeline regeneration job {} created for user {}", jobId, userId);

            return Response.ok(ApiResponse.success(java.util.Map.of("jobId", jobId.toString()))).build();

        } catch (IllegalStateException e) {
            // User already has an active job
            log.warn("Timeline regeneration rejected for user {}: {}", userId, e.getMessage());
            return Response.status(Response.Status.CONFLICT)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();

        } catch (Exception e) {
            log.error("Failed to start timeline regeneration for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to start timeline regeneration: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get the progress of a specific timeline generation job
     *
     * @param jobId The job ID
     * @return Job progress information
     */
    @GET
    @Path("/jobs/{jobId}")
    @RolesAllowed({"USER", "ADMIN"})
    public Response getJobProgress(@PathParam("jobId") String jobId) {
        UUID userId = currentUserService.getCurrentUserId();

        try {
            UUID jobUuid = UUID.fromString(jobId);
            var jobProgress = jobProgressService.getJobProgress(jobUuid);

            if (jobProgress.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("Job not found"))
                        .build();
            }

            // Verify the job belongs to the current user
            if (!jobProgress.get().getUserId().equals(userId)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(ApiResponse.error("Access denied"))
                        .build();
            }

            return Response.ok(ApiResponse.success(jobProgress.get())).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid job ID format"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to get job progress for job {}", jobId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get job progress: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get the active timeline generation job for the current user, if any
     *
     * @return Active job information or empty if no active job
     */
    @GET
    @Path("/jobs/active")
    @RolesAllowed({"USER", "ADMIN"})
    public Response getActiveJob() {
        UUID userId = currentUserService.getCurrentUserId();

        try {
            var activeJob = jobProgressService.getUserActiveJob(userId);

            if (activeJob.isEmpty()) {
                return Response.ok(ApiResponse.success(null)).build();
            }

            return Response.ok(ApiResponse.success(activeJob.get())).build();

        } catch (Exception e) {
            log.error("Failed to get active job for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get active job: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get the active timeline generation job for the current user, if any
     *
     * @return Active job information or empty if no active job
     */
    @GET
    @Path("/jobs/history")
    @RolesAllowed({"USER", "ADMIN"})
    public Response getJobHistory() {
        UUID userId = currentUserService.getCurrentUserId();

        try {
            List<TimelineJobProgress> historyJobs = jobProgressService.getUserHistoryJobs(userId);

            if (historyJobs == null || historyJobs.isEmpty()) {
                return Response.ok(ApiResponse.success(null)).build();
            }

            return Response.ok(ApiResponse.success(historyJobs)).build();

        } catch (Exception e) {
            log.error("Failed to get history jobs for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get history jobs: " + e.getMessage()))
                    .build();
        }
    }
}