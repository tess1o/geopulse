package org.github.tess1o.geopulse.digest.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.digest.model.HeatmapDataPoint;
import org.github.tess1o.geopulse.digest.model.HeatmapLayer;
import org.github.tess1o.geopulse.digest.service.DigestService;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

/**
 * REST resource exposing heatmap location data for the Rewind (TimeDigest)
 * page.
 * Returns all named locations visited during the requested period together with
 * their total dwell time and visit count so the frontend can render a heatmap.
 */
@Path("/api/digest/heatmap")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
@Tag(name = "User: Digests", description = "Read digest heatmap data for monthly, yearly, and custom ranges.")
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
    @APIResponse(responseCode = "200", description = "Monthly heatmap retrieved")
    @APIResponse(responseCode = "400", description = "Invalid heatmap period or layer")
    public List<HeatmapDataPoint> getMonthlyHeatmap(
            @QueryParam("year") int year,
            @QueryParam("month") int month,
            @QueryParam("layer") String layer) {

        var user = currentUserService.getCurrentUser();
        UUID userId = user.getId();
        String timezone = user.getTimezone();

        if (year < 2000 || year > 2100) {
            throw problem(INVALID_DIGEST_YEAR, "Invalid year. Must be between 2000 and 2100",
                    Map.of("year", year, "min", 2000, "max", 2100));
        }

        if (month < 1 || month > 12) {
            throw problem(INVALID_DIGEST_MONTH, "Invalid month. Must be between 1 and 12",
                    Map.of("month", month, "min", 1, "max", 12));
        }

        HeatmapLayer heatmapLayer = HeatmapLayer.fromString(layer);
        if (heatmapLayer == null) {
            throw problem(INVALID_HEATMAP_LAYER, "Invalid layer. Must be one of: stays, trips, combined",
                    Map.of("layer", String.valueOf(layer)));
        }

        log.info("Received request for monthly heatmap: user={}, year={}, month={}", userId, year, month);

        return digestService.getMonthlyHeatmap(userId, year, month, timezone, heatmapLayer);
    }

    /**
     * Get heatmap data for an entire year.
     * <p>
     * Query param: {@code year} (required)
     */
    @GET
    @Path("/yearly")
    @APIResponse(responseCode = "200", description = "Yearly heatmap retrieved")
    @APIResponse(responseCode = "400", description = "Invalid heatmap year or layer")
    public List<HeatmapDataPoint> getYearlyHeatmap(@QueryParam("year") int year,
                                                    @QueryParam("layer") String layer) {

        var user = currentUserService.getCurrentUser();
        UUID userId = user.getId();
        String timezone = user.getTimezone();

        if (year < 2000 || year > 2100) {
            throw problem(INVALID_DIGEST_YEAR, "Invalid year. Must be between 2000 and 2100",
                    Map.of("year", year, "min", 2000, "max", 2100));
        }

        HeatmapLayer heatmapLayer = HeatmapLayer.fromString(layer);
        if (heatmapLayer == null) {
            throw problem(INVALID_HEATMAP_LAYER, "Invalid layer. Must be one of: stays, trips, combined",
                    Map.of("layer", String.valueOf(layer)));
        }

        log.info("Received request for yearly heatmap: user={}, year={}", userId, year);

        return digestService.getYearlyHeatmap(userId, year, timezone, heatmapLayer);
    }

    /**
     * Get heatmap data for a custom time range.
     * <p>
     * Query params: {@code startTime} (required, ISO-8601), {@code endTime} (required, ISO-8601)
     */
    @GET
    @Path("/range")
    @APIResponse(responseCode = "200", description = "Heatmap range retrieved")
    @APIResponse(responseCode = "400", description = "Invalid heatmap range or layer")
    public List<HeatmapDataPoint> getRangeHeatmap(@QueryParam("startTime") String startTime,
                                                   @QueryParam("endTime") String endTime,
                                                   @QueryParam("layer") String layer) {

        var user = currentUserService.getCurrentUser();
        UUID userId = user.getId();

        if (startTime == null || startTime.isBlank() || endTime == null || endTime.isBlank()) {
            throw problem(INVALID_HEATMAP_RANGE, "startTime and endTime are required");
        }

        Instant start;
        Instant end;
        try {
            start = Instant.parse(startTime);
            end = Instant.parse(endTime);
        } catch (DateTimeParseException e) {
            throw problem(INVALID_HEATMAP_RANGE, "startTime and endTime must be valid ISO-8601 timestamps");
        }

        if (end.isBefore(start)) {
            throw problem(INVALID_HEATMAP_RANGE, "endTime must be after startTime");
        }

        HeatmapLayer heatmapLayer = HeatmapLayer.fromString(layer);
        if (heatmapLayer == null) {
            throw problem(INVALID_HEATMAP_LAYER, "Invalid layer. Must be one of: stays, trips, combined",
                    Map.of("layer", String.valueOf(layer)));
        }

        log.info("Received request for range heatmap: user={}, start={}, end={}", userId, start, end);

        return digestService.getHeatmapForRange(userId, start, end, heatmapLayer);
    }
}
