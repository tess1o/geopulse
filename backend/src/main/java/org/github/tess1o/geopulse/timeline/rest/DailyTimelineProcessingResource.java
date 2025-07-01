package org.github.tess1o.geopulse.timeline.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.timeline.service.DailyTimelineProcessingService;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * REST endpoints for managing daily timeline processing.
 * Provides manual triggers and monitoring capabilities for background timeline generation.
 */
@Path("/api/timeline/daily-processing")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@RequestScoped
public class DailyTimelineProcessingResource {

    @Inject
    DailyTimelineProcessingService dailyProcessingService;

    @ConfigProperty(name = "geopulse.timeline.daily-processing.batch-size", defaultValue = "20")
    int batchSize;

    @ConfigProperty(name = "geopulse.timeline.daily-processing.enabled", defaultValue = "true")
    boolean processingEnabled;

    /**
     * Manually trigger timeline processing for a specific date.
     * Useful for backfilling data or reprocessing after fixes.
     *
     * @param date The date in YYYY-MM-DD format to process
     * @return Processing statistics
     */
    @POST
    @Path("/trigger")
    @RolesAllowed("ADMIN") // Only admins can trigger manual processing
    public Response triggerProcessingForDate(@QueryParam("date") String date) {
        if (date == null || date.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Date parameter is required in YYYY-MM-DD format"))
                    .build();
        }

        try {
            LocalDate targetDate = LocalDate.parse(date);

            log.info("Manual timeline processing triggered for date: {} by admin", targetDate);

            DailyTimelineProcessingService.ProcessingStatistics stats =
                    dailyProcessingService.processTimelineForDate(targetDate);

            return Response.ok(ApiResponse.success(stats)).build();

        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid date format. Use YYYY-MM-DD format"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to trigger timeline processing for date {}", date, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to process timeline: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Trigger processing for yesterday's data.
     * Convenience endpoint for reprocessing the most recent completed day.
     */
    @POST
    @Path("/trigger/yesterday")
    @RolesAllowed("ADMIN")
    public Response triggerProcessingForYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        try {
            log.info("Manual timeline processing triggered for yesterday ({}) by admin", yesterday);

            DailyTimelineProcessingService.ProcessingStatistics stats =
                    dailyProcessingService.processTimelineForDate(yesterday);

            return Response.ok(ApiResponse.success(stats)).build();

        } catch (Exception e) {
            log.error("Failed to trigger timeline processing for yesterday ({})", yesterday, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to process timeline: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get information about the daily processing service.
     * Returns configuration and status information.
     */
    @GET
    @Path("/info")
    @RolesAllowed("ADMIN")
    public Response getProcessingInfo() {
        try {
            var info = new ProcessingInfo(
                    "Daily timeline processing runs at 00:05 UTC",
                    "Processes previous day's timeline data for all active users",
                    "0 5 0 * * ?", // cron expression
                    batchSize,
                    processingEnabled,
                    LocalDate.now().minusDays(1) // last expected processing date
            );

            return Response.ok(ApiResponse.success(info)).build();

        } catch (Exception e) {
            log.error("Failed to retrieve processing info", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve processing info: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Information about the daily processing service.
     */
    public record ProcessingInfo(String schedule, String description, String cronExpression, int batchSize,
                                 boolean enabled, LocalDate lastExpectedProcessingDate) {
    }
}