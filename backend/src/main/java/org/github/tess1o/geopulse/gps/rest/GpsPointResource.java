package org.github.tess1o.geopulse.gps.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
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
import org.github.tess1o.geopulse.user.model.MeasureUnit;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
    @RolesAllowed({"USER", "ADMIN"})
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
     * Get summary statistics for GPS points with optional filters.
     * This endpoint requires authentication and uses the user's stored timezone.
     *
     * @return Summary statistics for the user's GPS points
     */
    @GET
    @Path("/summary")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"USER", "ADMIN"})
    public Response getGpsPointSummary(
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime,
            @QueryParam("accuracyMin") Double accuracyMin,
            @QueryParam("accuracyMax") Double accuracyMax,
            @QueryParam("speedMin") Double speedMin,
            @QueryParam("speedMax") Double speedMax,
            @QueryParam("sourceTypes") String sourceTypes) {
        UserEntity user = currentUserService.getCurrentUser();
        log.info("Received request to get GPS point summary for user {} with timezone {}", user.getId(), user.getTimezone());

        try {
            // Parse filters
            GpsPointFilterDTO filters = buildFilters(startTime, endTime, accuracyMin, accuracyMax,
                    speedMin, speedMax, sourceTypes);

            GpsPointSummaryDTO summary;

            try {
                ZoneId userTimezone = ZoneId.of(user.getTimezone());
                summary = gpsPointService.getGpsPointSummaryWithFilters(user.getId(), userTimezone, filters);
            } catch (DateTimeException e) {
                log.warn("Invalid timezone '{}' for user {}, falling back to UTC", user.getTimezone(), user.getId());
                summary = gpsPointService.getGpsPointSummaryWithFilters(user.getId(), ZoneId.of("UTC"), filters);
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
     * Get paginated GPS points with optional filtering and sorting.
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
    @RolesAllowed({"USER", "ADMIN"})
    public Response getGpsPoints(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime,
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @QueryParam("sortOrder") @DefaultValue("desc") String sortOrder,
            @QueryParam("accuracyMin") Double accuracyMin,
            @QueryParam("accuracyMax") Double accuracyMax,
            @QueryParam("speedMin") Double speedMin,
            @QueryParam("speedMax") Double speedMax,
            @QueryParam("sourceTypes") String sourceTypes) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to get GPS points for user {} - page: {}, limit: {}, filters: accuracyMin: {}, accuracyMax: {}, speedMin: {}, speedMax: {}",
                userId, page, limit, accuracyMin, accuracyMax, speedMin, speedMax);

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

            // Build filters
            GpsPointFilterDTO filters = buildFilters(startTime != null ? startTime : (startDate != null ? startDate : null),
                    endTime != null ? endTime : (endDate != null ? endDate : null),
                    accuracyMin, accuracyMax, speedMin, speedMax, sourceTypes);

            GpsPointPageDTO result = gpsPointService.getGpsPointsPageWithFilters(userId, filters, page, limit, sortBy, sortOrder);
            return Response.ok(ApiResponse.success(result)).build();
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid date/time format"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to retrieve GPS points for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve GPS points: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Export GPS points as CSV with streaming to prevent OOM.
     * This endpoint requires authentication and supports all filters.
     *
     * @param startDate Start date filter (format: YYYY-MM-DD)
     * @param endDate   End date filter (format: YYYY-MM-DD)
     * @return CSV file with GPS points (streamed)
     */
    @GET
    @Path("/export")
    @Produces("text/csv")
    @RolesAllowed({"USER", "ADMIN"})
    public Response exportGpsPoints(
            @QueryParam("startDate") String startDate,
            @QueryParam("endDate") String endDate,
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime,
            @QueryParam("accuracyMin") Double accuracyMin,
            @QueryParam("accuracyMax") Double accuracyMax,
            @QueryParam("speedMin") Double speedMin,
            @QueryParam("speedMax") Double speedMax,
            @QueryParam("sourceTypes") String sourceTypes,
            @QueryParam("ids") String ids) {
        UserEntity user = currentUserService.getCurrentUser();
        log.info("Received request to export GPS points for user {} with filters", user.getId());

        try {
            // Build filters
            GpsPointFilterDTO filters = buildFilters(
                    startTime != null ? startTime : startDate,
                    endTime != null ? endTime : endDate,
                    accuracyMin, accuracyMax, speedMin, speedMax, sourceTypes);

            // If IDs are provided, add them to filters (overrides other filters)
            if (ids != null && !ids.trim().isEmpty()) {
                try {
                    List<Long> gpsPointIds = Arrays.stream(ids.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(Long::parseLong)
                            .collect(Collectors.toList());
                    filters.setGpsPointIds(gpsPointIds);
                    log.info("Exporting {} specific GPS points by IDs", gpsPointIds.size());
                } catch (NumberFormatException e) {
                    log.warn("Invalid GPS point IDs format: {}", ids, e);
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(ApiResponse.error("Invalid GPS point IDs format"))
                            .build();
                }
            }

            // Create streaming output to avoid loading all data into memory
            StreamingOutput stream = output -> {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                    // Write CSV header
                    if (user.getMeasureUnit() == MeasureUnit.METRIC) {
                        writer.write("timestamp,latitude,longitude,accuracy,battery,velocity(km/h),altitude,sourceType\n");
                    } else {
                        writer.write("timestamp,latitude,longitude,accuracy,battery,velocity(mph),altitude,sourceType\n");
                    }

                    // Stream GPS points in batches of 1000
                    gpsPointService.streamGpsPointsForExport(user.getId(), filters, 1000, batch -> {
                        try {
                            for (GpsPointEntity point : batch) {
                                writer.write(formatCsvRow(point, user.getMeasureUnit()));
                            }
                            writer.flush(); // Flush after each batch
                        } catch (Exception e) {
                            throw new RuntimeException("Error writing CSV batch", e);
                        }
                    });
                }
            };

            String filename = String.format("gps-points-export-%s.csv",
                    startDate != null && endDate != null ? startDate + "_" + endDate : "all");

            return Response.ok(stream)
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", "text/csv; charset=utf-8")
                    .build();
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid date/time format"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to export GPS points for user {}", user.getId(), e);
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
     * Format a single GPS point as a CSV row.
     *
     * @param point       GPS point entity
     * @param measureUnit User's measurement unit preference
     * @return CSV row string
     */
    private String formatCsvRow(GpsPointEntity point, MeasureUnit measureUnit) {
        StringBuilder row = new StringBuilder();

        Double velocity = point.getVelocity() != null ? point.getVelocity() : 0.0;
        if (measureUnit == MeasureUnit.IMPERIAL) {
            velocity = velocity * 0.621371; // Convert km/h to mph
        }

        row.append(point.getTimestamp().toString()).append(",");
        row.append(point.getLatitude()).append(",");
        row.append(point.getLongitude()).append(",");
        row.append(point.getAccuracy() != null ? point.getAccuracy() : "").append(",");
        row.append(point.getBattery() != null ? point.getBattery() : "").append(",");
        row.append(velocity).append(",");
        row.append(point.getAltitude() != null ? point.getAltitude() : "").append(",");
        row.append(point.getSourceType().name()).append("\n");

        return row.toString();
    }

    /**
     * Build filter DTO from query parameters.
     *
     * @return GpsPointFilterDTO with all filters
     */
    private GpsPointFilterDTO buildFilters(String startTime, String endTime,
                                           Double accuracyMin, Double accuracyMax,
                                           Double speedMin, Double speedMax,
                                           String sourceTypes) {
        GpsPointFilterDTO.GpsPointFilterDTOBuilder builder = GpsPointFilterDTO.builder();

        // Parse time range
        if (startTime != null && !startTime.trim().isEmpty()) {
            try {
                Instant start = Instant.parse(startTime.trim());
                builder.startTime(start);
            } catch (DateTimeParseException e) {
                // Try parsing as date
                try {
                    LocalDate date = LocalDate.parse(startTime.trim());
                    builder.startTime(date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
                } catch (DateTimeParseException ex) {
                    log.warn("Failed to parse startTime: {}", startTime);
                }
            }
        }

        if (endTime != null && !endTime.trim().isEmpty()) {
            try {
                Instant end = Instant.parse(endTime.trim());
                builder.endTime(end);
            } catch (DateTimeParseException e) {
                // Try parsing as date
                try {
                    LocalDate date = LocalDate.parse(endTime.trim());
                    builder.endTime(date.atTime(23, 59, 59).toInstant(java.time.ZoneOffset.UTC));
                } catch (DateTimeParseException ex) {
                    log.warn("Failed to parse endTime: {}", endTime);
                }
            }
        }

        // Set filter values
        builder.accuracyMin(accuracyMin)
                .accuracyMax(accuracyMax)
                .speedMin(speedMin)
                .speedMax(speedMax);

        // Parse source types
        if (sourceTypes != null && !sourceTypes.trim().isEmpty()) {
            try {
                List<GpsSourceType> sourceTypeList = Arrays.stream(sourceTypes.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(GpsSourceType::valueOf)
                        .collect(Collectors.toList());
                builder.sourceTypes(sourceTypeList);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid source type in: {}", sourceTypes, e);
            }
        }

        return builder.build();
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
    @RolesAllowed({"USER", "ADMIN"})
    public Response updateGpsPoint(@PathParam("pointId") Long pointId, @Valid EditGpsPointDto editDto) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to update GPS point {} for user {}", pointId, userId);

        try {
            GpsPointDTO updatedPoint = gpsPointService.updateGpsPoint(pointId, editDto, userId);
            return Response.ok(ApiResponse.success(updatedPoint)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("GPS point not found"))
                    .build();
        } catch (ForbiddenException e) {
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
    @RolesAllowed({"USER", "ADMIN"})
    public Response deleteGpsPoint(@PathParam("pointId") Long pointId) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to delete GPS point {} for user {}", pointId, userId);

        try {
            gpsPointService.deleteGpsPoint(pointId, userId);
            return Response.noContent().build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("GPS point not found"))
                    .build();
        } catch (ForbiddenException e) {
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
    @RolesAllowed({"USER", "ADMIN"})
    public Response deleteGpsPoints(@Valid BulkDeleteGpsPointsDto bulkDeleteDto) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to delete {} GPS points for user {}",
                bulkDeleteDto.getGpsPointIds().size(), userId);

        try {
            int deletedCount = gpsPointService.deleteGpsPoints(bulkDeleteDto.getGpsPointIds(), userId);
            return Response.ok(ApiResponse.success(Map.of("deletedCount", deletedCount))).build();
        } catch (ForbiddenException e) {
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

    @GET
    @Path("/last-known-position")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"USER", "ADMIN"})
    public Response getLastKnownPosition() {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to get last known position for user {}", userId);
        Optional<GpsPointDTO> lastPosition = gpsPointService.getLastKnownPosition(userId);
        return Response.ok(ApiResponse.success(lastPosition.orElseGet(() -> null))).build();
    }
}