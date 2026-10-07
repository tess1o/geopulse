package org.github.tess1o.geopulse.digest.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

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
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

/**
 * REST resource exposing heatmap location data for the Rewind (TimeDigest)
 * page.
 * Returns all named locations visited during the requested period together with
 * their total dwell time and visit count so the frontend can render a heatmap.
 */
@Path("/digest-heatmaps")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
@Tag(name = ApiTags.DIGESTS)
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
    @Operation(summary = "Get a monthly heatmap",
            description = "Returns heatmap points for a calendar month in the user's timezone. Each point has "
                    + "coordinates, a place name when known, the number of visits, and the time spent, for drawing "
                    + "a heatmap.")
    public List<HeatmapDataPoint> getMonthlyHeatmap(
            @Parameter(description = "Year, from 2000 to 2100.", example = "2025")
            @QueryParam("year") int year,
            @Parameter(description = "Month, from 1 to 12.", example = "6")
            @QueryParam("month") int month,
            @Parameter(description = "What to include: `stays`, `trips`, or `combined` (default).", example = "stays")
            @QueryParam("layer") String layer) {

        var user = currentUserService.getCurrentUser();
        UUID userId = user.getId();
        String timezone = user.getTimezone();

        if (year < 2000 || year > 2100) {
            throw new GeoPulseException(INVALID_DIGEST_YEAR, "Invalid year. Must be between 2000 and 2100",
                    Map.of("year", year, "min", 2000, "max", 2100));
        }

        if (month < 1 || month > 12) {
            throw new GeoPulseException(INVALID_DIGEST_MONTH, "Invalid month. Must be between 1 and 12",
                    Map.of("month", month, "min", 1, "max", 12));
        }

        HeatmapLayer heatmapLayer = HeatmapLayer.fromString(layer);
        if (heatmapLayer == null) {
            throw new GeoPulseException(INVALID_HEATMAP_LAYER, "Invalid layer. Must be one of: stays, trips, combined",
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
    @Operation(summary = "Get a yearly heatmap",
            description = "Returns heatmap points for a calendar year in the user's timezone. Each point has "
                    + "coordinates, a place name when known, the number of visits, and the time spent, for drawing "
                    + "a heatmap.")
    public List<HeatmapDataPoint> getYearlyHeatmap(
            @Parameter(description = "Year, from 2000 to 2100.", example = "2025")
            @QueryParam("year") int year,
            @Parameter(description = "What to include: `stays`, `trips`, or `combined` (default).", example = "stays")
            @QueryParam("layer") String layer) {

        var user = currentUserService.getCurrentUser();
        UUID userId = user.getId();
        String timezone = user.getTimezone();

        if (year < 2000 || year > 2100) {
            throw new GeoPulseException(INVALID_DIGEST_YEAR, "Invalid year. Must be between 2000 and 2100",
                    Map.of("year", year, "min", 2000, "max", 2100));
        }

        HeatmapLayer heatmapLayer = HeatmapLayer.fromString(layer);
        if (heatmapLayer == null) {
            throw new GeoPulseException(INVALID_HEATMAP_LAYER, "Invalid layer. Must be one of: stays, trips, combined",
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
    @Operation(summary = "Get a heatmap for a range",
            description = "Returns heatmap points for a custom time range. Each point has coordinates, a place name "
                    + "when known, the number of visits, and the time spent, for drawing a heatmap.")
    public List<HeatmapDataPoint> getRangeHeatmap(
            @Parameter(description = "Start of the time range, as an ISO-8601 instant. Required.",
                    example = "2025-06-01T00:00:00Z")
            @QueryParam("from") String startTime,
            @Parameter(description = "End of the time range, as an ISO-8601 instant. Required.",
                    example = "2025-06-30T23:59:59Z")
            @QueryParam("to") String endTime,
            @Parameter(description = "What to include: `stays`, `trips`, or `combined` (default).", example = "stays")
            @QueryParam("layer") String layer) {

        var user = currentUserService.getCurrentUser();
        UUID userId = user.getId();

        if (startTime == null || startTime.isBlank() || endTime == null || endTime.isBlank()) {
            throw new GeoPulseException(INVALID_HEATMAP_RANGE, "startTime and endTime are required");
        }

        Instant start;
        Instant end;
        try {
            start = Instant.parse(startTime);
            end = Instant.parse(endTime);
        } catch (DateTimeParseException e) {
            throw new GeoPulseException(INVALID_HEATMAP_RANGE, "startTime and endTime must be valid ISO-8601 timestamps", e);
        }

        if (end.isBefore(start)) {
            throw new GeoPulseException(INVALID_HEATMAP_RANGE, "endTime must be after startTime");
        }

        HeatmapLayer heatmapLayer = HeatmapLayer.fromString(layer);
        if (heatmapLayer == null) {
            throw new GeoPulseException(INVALID_HEATMAP_LAYER, "Invalid layer. Must be one of: stays, trips, combined",
                    Map.of("layer", String.valueOf(layer)));
        }

        log.info("Received request for range heatmap: user={}, start={}, end={}", userId, start, end);

        return digestService.getHeatmapForRange(userId, start, end, heatmapLayer);
    }
}
