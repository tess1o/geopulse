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
import org.github.tess1o.geopulse.gps.service.simplification.PathSimplificationService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.io.StringWriter;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
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
    private final PathSimplificationService pathSimplificationService;
    private final TimelineConfigurationProvider configurationProvider;

    @Inject
    public GpsPointResource(GpsPointService gpsPointService,
                            CurrentUserService currentUserService,
                            PathSimplificationService pathSimplificationService,
                            TimelineConfigurationProvider configurationProvider) {
        this.gpsPointService = gpsPointService;
        this.currentUserService = currentUserService;
        this.pathSimplificationService = pathSimplificationService;
        this.configurationProvider = configurationProvider;
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
        UserEntity user = currentUserService.getCurrentUser();
        log.info("Received request to get GPS point path for user {} between {} and {}", user.getEmail(), startTime, endTime);

        try {
            Instant start = startTime != null ? Instant.parse(startTime) : Instant.EPOCH;
            Instant end = endTime != null ? Instant.parse(endTime) : Instant.now();
            GpsPointPathDTO path = gpsPointService.getGpsPointPath(user.getId(), start, end);
            TimelineConfig config = configurationProvider.getConfigurationForUser(user.getId());
            List<? extends GpsPoint> simplifyPath = pathSimplificationService.simplify(path.getPoints(), config);
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
     * This endpoint requires authentication and uses the user's stored timezone.
     *
     * @return Summary statistics for the user's GPS points
     */
    @GET
    @Path("/summary")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("USER")
    public Response getGpsPointSummary() {
        UserEntity user = currentUserService.getCurrentUser();
        log.info("Received request to get GPS point summary for user {} with timezone {}", user.getId(), user.getTimezone());

        try {
            GpsPointSummaryDTO summary;

            try {
                ZoneId userTimezone = ZoneId.of(user.getTimezone());
                summary = gpsPointService.getGpsPointSummary(user.getId(), userTimezone);
            } catch (DateTimeException e) {
                log.warn("Invalid timezone '{}' for user {}, falling back to UTC", user.getTimezone(), user.getId());
                summary = gpsPointService.getGpsPointSummary(user.getId());
            }

            return Response.ok(ApiResponse.success(summary)).build();
        } catch (Exception e) {
            log.error("Failed to retrieve GPS point summary for user {}", user.getId(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve GPS point summary: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get paginated GPS points with optional date filtering and sorting.
     * This endpoint requires authentication.
     *
     * @param page      Page number (default: 1)
     * @param limit     Number of items per page (default: 50)
     * @param startDate Start date filter (format: YYYY-MM-DD)
     * @param endDate   End date filter (format: YYYY-MM-DD)
     * @param sortBy    Field to sort by (default: timestamp)
     * @param sortOrder Sort order: asc or desc (default: desc)
     * @return Paginated GPS points
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("USER")
    public Response getGpsPoints(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime,
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @QueryParam("sortOrder") @DefaultValue("desc") String sortOrder) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to get GPS points for user {} - page: {}, limit: {}, startDate: {}, endDate: {}, startTime: {}, endTime: {}, sortBy: {}, sortOrder: {}",
                userId, page, limit, startDate, endDate, startTime, endTime, sortBy, sortOrder);

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

            // Validate sort order
            if (!sortOrder.equalsIgnoreCase("asc") && !sortOrder.equalsIgnoreCase("desc")) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("Sort order must be 'asc' or 'desc'"))
                        .build();
            }

            // Parse date/time parameters - prioritize precise timestamps over dates
            Instant start = parseDateTime(startTime, startDate, true);
            Instant end = parseDateTime(endTime, endDate, false);

            GpsPointPageDTO result = gpsPointService.getGpsPointsPage(userId, start, end, page, limit, sortBy, sortOrder);
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
            @QueryParam("endDate") String endDate,
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to export GPS points for user {} - startDate: {}, endDate: {}, startTime: {}, endTime: {}",
                userId, startDate, endDate, startTime, endTime);

        try {
            // Parse date/time parameters - prioritize precise timestamps over dates
            Instant start = parseDateTime(startTime, startDate, true);
            Instant end = parseDateTime(endTime, endDate, false);

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
     * Parse date/time parameters with fallback support.
     * Prioritizes ISO-8601 timestamps over date-only strings.
     */
    private Instant parseDateTime(String timeStr, String dateStr, boolean isStartDate) {
        // First try to parse as ISO-8601 timestamp (e.g., 2025-08-10T00:00:00.000Z)
        if (timeStr != null && !timeStr.trim().isEmpty()) {
            try {
                return Instant.parse(timeStr.trim());
            } catch (DateTimeParseException e) {
                throw new DateTimeParseException("Invalid timestamp format. Use ISO-8601 format (e.g., 2025-08-10T00:00:00.000Z)", timeStr, 0);
            }
        }
        
        // Fallback to date-only parsing (e.g., 2025-08-10)
        return parseDate(dateStr, isStartDate);
    }

    /**
     * Parse date string to Instant.
     *
     * @param dateStr     Date string in YYYY-MM-DD format
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

    /**
     * Update a GPS point.
     * This endpoint requires authentication.
     *
     * @param pointId The ID of the GPS point to update
     * @param editDto The update data
     * @return The updated GPS point
     */
    @PUT
    @Path("/{pointId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("USER")
    public Response updateGpsPoint(@PathParam("pointId") Long pointId, @Valid EditGpsPointDto editDto) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to update GPS point {} for user {}", pointId, userId);

        try {
            GpsPointDTO updatedPoint = gpsPointService.updateGpsPoint(pointId, editDto, userId);
            return Response.ok(ApiResponse.success(updatedPoint)).build();
        } catch (jakarta.ws.rs.NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("GPS point not found"))
                    .build();
        } catch (jakarta.ws.rs.ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Access denied"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to update GPS point {} for user {}", pointId, userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to update GPS point: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Delete a GPS point.
     * This endpoint requires authentication.
     *
     * @param pointId The ID of the GPS point to delete
     * @return 204 No Content if successful
     */
    @DELETE
    @Path("/{pointId}")
    @RolesAllowed("USER")
    public Response deleteGpsPoint(@PathParam("pointId") Long pointId) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to delete GPS point {} for user {}", pointId, userId);

        try {
            gpsPointService.deleteGpsPoint(pointId, userId);
            return Response.noContent().build();
        } catch (jakarta.ws.rs.NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("GPS point not found"))
                    .build();
        } catch (jakarta.ws.rs.ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Access denied"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to delete GPS point {} for user {}", pointId, userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to delete GPS point: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Delete multiple GPS points.
     * This endpoint requires authentication.
     *
     * @param bulkDeleteDto The bulk delete request containing GPS point IDs
     * @return Response with the number of deleted points
     */
    @POST
    @Path("/bulk")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("USER")
    public Response deleteGpsPoints(@Valid BulkDeleteGpsPointsDto bulkDeleteDto) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to delete {} GPS points for user {}",
                bulkDeleteDto.getGpsPointIds().size(), userId);

        try {
            int deletedCount = gpsPointService.deleteGpsPoints(bulkDeleteDto.getGpsPointIds(), userId);
            return Response.ok(ApiResponse.success(Map.of("deletedCount", deletedCount))).build();
        } catch (jakarta.ws.rs.ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Access denied"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to delete GPS points for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to delete GPS points: " + e.getMessage()))
                    .build();
        }
    }

}