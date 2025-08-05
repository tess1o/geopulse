package org.github.tess1o.geopulse.gps.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.gps.model.*;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.timeline.simplification.GpsPathSimplifier;

import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * REST resource for GPS point data.
 */
@Path("/api/gps")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
public class GpsPointResource {

    private final GpsPointService gpsPointService;
    private final CurrentUserService currentUserService;

    @Inject
    public GpsPointResource(GpsPointService gpsPointService, CurrentUserService currentUserService) {
        this.gpsPointService = gpsPointService;
        this.currentUserService = currentUserService;
    }

    /**
     * Get a GPS point path for a user within a specified time period.
     * This endpoint requires authentication.
     *
     * @param startTime The start of the time period (ISO-8601 format)
     * @param endTime   The end of the time period (ISO-8601 format)
     * @return The GPS point path
     */
    @GET
    @Path("/path")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("USER")
    public Response getGpsPointPath(
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to get GPS point path for user {} between {} and {}", userId, startTime, endTime);

        try {
            Instant start = startTime != null ? Instant.parse(startTime) : Instant.EPOCH;
            Instant end = endTime != null ? Instant.parse(endTime) : Instant.now();
            GpsPointPathDTO path = gpsPointService.getGpsPointPath(userId, start, end);
            List<GpsPointPathPointDTO> simplifyPath = GpsPathSimplifier.simplifyPath(path.getPoints(), 10);
            path.setPoints(simplifyPath);
            return Response.ok(ApiResponse.success(path)).build();
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid time format. Use ISO-8601 format (e.g., 2023-01-01T00:00:00Z)"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve GPS point path: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get summary statistics for GPS points.
     * This endpoint requires authentication.
     *
     * @return Summary statistics for the user's GPS points
     */
    @GET
    @Path("/summary")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("USER")
    public Response getGpsPointSummary() {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to get GPS point summary for user {}", userId);

        try {
            GpsPointSummaryDTO summary = gpsPointService.getGpsPointSummary(userId);
            return Response.ok(ApiResponse.success(summary)).build();
        } catch (Exception e) {
            log.error("Failed to retrieve GPS point summary for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve GPS point summary: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get paginated GPS points with optional date filtering.
     * This endpoint requires authentication.
     *
     * @param page      Page number (default: 1)
     * @param limit     Number of items per page (default: 50)
     * @param startDate Start date filter (format: YYYY-MM-DD)
     * @param endDate   End date filter (format: YYYY-MM-DD)
     * @return Paginated GPS points
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("USER")
    public Response getGpsPoints(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to get GPS points for user {} - page: {}, limit: {}, startDate: {}, endDate: {}", 
                userId, page, limit, startDate, endDate);

        try {
            // Validate pagination parameters
            if (page < 1) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("Page number must be greater than 0"))
                        .build();
            }
            if (limit < 1 || limit > 1000) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("Limit must be between 1 and 1000"))
                        .build();
            }

            // Parse date parameters
            Instant start = parseDate(startDate, true);
            Instant end = parseDate(endDate, false);

            GpsPointPageDTO result = gpsPointService.getGpsPointsPage(userId, start, end, page, limit);
            return Response.ok(ApiResponse.success(result)).build();
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid date format. Use YYYY-MM-DD format"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve GPS points: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Export GPS points as CSV.
     * This endpoint requires authentication.
     *
     * @param startDate Start date filter (format: YYYY-MM-DD)
     * @param endDate   End date filter (format: YYYY-MM-DD)
     * @return CSV file with GPS points
     */
    @GET
    @Path("/export")
    @Produces("text/csv")
    @RolesAllowed("USER")
    public Response exportGpsPoints(
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to export GPS points for user {} - startDate: {}, endDate: {}", 
                userId, startDate, endDate);

        try {
            // Parse date parameters
            Instant start = parseDate(startDate, true);
            Instant end = parseDate(endDate, false);

            List<GpsPointEntity> points = gpsPointService.getGpsPointsForExport(userId, start, end);
            String csv = generateCsv(points);

            String filename = String.format("gps-points-export-%s.csv", 
                    startDate != null && endDate != null ? startDate + "_" + endDate : "all");

            return Response.ok(csv)
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .build();
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid date format. Use YYYY-MM-DD format"))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to export GPS points: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Parse date string to Instant.
     * 
     * @param dateStr Date string in YYYY-MM-DD format
     * @param isStartDate True if this is a start date (start of day), false for end date (end of day)
     * @return Parsed Instant or null if dateStr is null
     */
    private Instant parseDate(String dateStr, boolean isStartDate) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return isStartDate ? Instant.EPOCH : Instant.now();
        }
        
        LocalDate date = LocalDate.parse(dateStr.trim());
        return isStartDate ? date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC) 
                          : date.atTime(23, 59, 59).toInstant(java.time.ZoneOffset.UTC);
    }

    /**
     * Generate CSV from GPS points.
     * 
     * @param points List of GPS points
     * @return CSV string
     */
    private String generateCsv(List<GpsPointEntity> points) {
        StringWriter writer = new StringWriter();
        writer.append("id,timestamp,latitude,longitude,accuracy,battery,velocity,altitude,sourceType\n");
        
        for (GpsPointEntity point : points) {
            writer.append(String.valueOf(point.getId())).append(",");
            writer.append(point.getTimestamp().toString()).append(",");
            writer.append(String.valueOf(point.getLatitude())).append(",");
            writer.append(String.valueOf(point.getLongitude())).append(",");
            writer.append(point.getAccuracy() != null ? String.valueOf(point.getAccuracy()) : "").append(",");
            writer.append(point.getBattery() != null ? String.valueOf(point.getBattery()) : "").append(",");
            writer.append(point.getVelocity() != null ? String.valueOf(point.getVelocity()) : "").append(",");
            writer.append(point.getAltitude() != null ? String.valueOf(point.getAltitude()) : "").append(",");
            writer.append(point.getSourceType().name()).append("\n");
        }
        
        return writer.toString();
    }

}