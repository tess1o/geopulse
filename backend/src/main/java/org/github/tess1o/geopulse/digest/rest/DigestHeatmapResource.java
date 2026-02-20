package org.github.tess1o.geopulse.digest.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.digest.model.HeatmapDataPoint;
import org.github.tess1o.geopulse.digest.model.HeatmapLayer;
import org.github.tess1o.geopulse.digest.service.DigestService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * REST resource exposing heatmap location data for the Rewind (TimeDigest)
 * page.
 * Returns all named locations visited during the requested period together with
 * their total dwell time and visit count so the frontend can render a heatmap.
 */
@Path("/api/digest/heatmap")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({ "USER", "ADMIN" })
@Slf4j
public class DigestHeatmapResource {

    @Inject
    DigestService digestService;

    @Inject
    CurrentUserService currentUserService;

    /**
     * Get heatmap data for a specific month.
     * <p>
     * Query params: {@code year} (required), {@code month} (required, 1-12)
     */
    @GET
    @Path("/monthly")
    public Response getMonthlyHeatmap(
            @QueryParam("year") int year,
            @QueryParam("month") int month,
            @QueryParam("layer") String layer) {

        var user = currentUserService.getCurrentUser();
        UUID userId = user.getId();
        String timezone = user.getTimezone();

        if (year < 2000 || year > 2100) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid year. Must be between 2000 and 2100"))
                    .build();
        }
        if (month < 1 || month > 12) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid month. Must be between 1 and 12"))
                    .build();
        }

        HeatmapLayer heatmapLayer = HeatmapLayer.fromString(layer);
        if (heatmapLayer == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid layer. Must be one of: stays, trips, combined"))
                    .build();
        }

        log.info("Received request for monthly heatmap: user={}, year={}, month={}", userId, year, month);

        try {
            List<HeatmapDataPoint> points = digestService.getMonthlyHeatmap(userId, year, month, timezone, heatmapLayer);
            return Response.ok(ApiResponse.success(points)).build();
        } catch (Exception e) {
            log.error("Failed to generate monthly heatmap for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to generate heatmap: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get heatmap data for an entire year.
     * <p>
     * Query param: {@code year} (required)
     */
    @GET
    @Path("/yearly")
    public Response getYearlyHeatmap(@QueryParam("year") int year,
                                     @QueryParam("layer") String layer) {

        var user = currentUserService.getCurrentUser();
        UUID userId = user.getId();
        String timezone = user.getTimezone();

        if (year < 2000 || year > 2100) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid year. Must be between 2000 and 2100"))
                    .build();
        }

        HeatmapLayer heatmapLayer = HeatmapLayer.fromString(layer);
        if (heatmapLayer == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid layer. Must be one of: stays, trips, combined"))
                    .build();
        }

        log.info("Received request for yearly heatmap: user={}, year={}", userId, year);

        try {
            List<HeatmapDataPoint> points = digestService.getYearlyHeatmap(userId, year, timezone, heatmapLayer);
            return Response.ok(ApiResponse.success(points)).build();
        } catch (Exception e) {
            log.error("Failed to generate yearly heatmap for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to generate heatmap: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get heatmap data for a custom time range.
     * <p>
     * Query params: {@code startTime} (required, ISO-8601), {@code endTime} (required, ISO-8601)
     */
    @GET
    @Path("/range")
    public Response getRangeHeatmap(@QueryParam("startTime") String startTime,
                                    @QueryParam("endTime") String endTime,
                                    @QueryParam("layer") String layer) {

        var user = currentUserService.getCurrentUser();
        UUID userId = user.getId();

        if (startTime == null || startTime.isBlank() || endTime == null || endTime.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("startTime and endTime are required"))
                    .build();
        }

        Instant start;
        Instant end;
        try {
            start = Instant.parse(startTime);
            end = Instant.parse(endTime);
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("startTime and endTime must be valid ISO-8601 timestamps"))
                    .build();
        }

        if (end.isBefore(start)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("endTime must be after startTime"))
                    .build();
        }

        HeatmapLayer heatmapLayer = HeatmapLayer.fromString(layer);
        if (heatmapLayer == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid layer. Must be one of: stays, trips, combined"))
                    .build();
        }

        log.info("Received request for range heatmap: user={}, start={}, end={}", userId, start, end);

        try {
            List<HeatmapDataPoint> points = digestService.getHeatmapForRange(userId, start, end, heatmapLayer);
            return Response.ok(ApiResponse.success(points)).build();
        } catch (Exception e) {
            log.error("Failed to generate range heatmap for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to generate heatmap: " + e.getMessage()))
                    .build();
        }
    }
}
