package org.github.tess1o.geopulse.gps.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.export.service.CsvExportService;
import org.github.tess1o.geopulse.gps.exceptions.GpsCoordinateDuplicateException;
import org.github.tess1o.geopulse.gps.model.*;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gps.service.simplification.PathSimplificationService;
import org.github.tess1o.geopulse.gps.service.simplification.TimelineSegmentBoundary;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.service.GpsSourceService;
import org.github.tess1o.geopulse.coverage.model.CoverageStatus;
import org.github.tess1o.geopulse.coverage.service.CoverageProcessingService;
import org.github.tess1o.geopulse.coverage.service.CoverageService;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;
import jakarta.validation.Valid;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.github.tess1o.geopulse.shared.api.ApiPaths;
import org.github.tess1o.geopulse.shared.api.PageResponse;
import org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.jboss.resteasy.reactive.RestHeader;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

/**
 * REST resource for GPS point data.
 */
@Path(ApiPaths.GPS_POINTS)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = "User: GPS Data", description = "Ingest, query, update, export, and delete GPS points.")
public class GpsPointResource {
    private static final int DEFAULT_RAW_MAP_POINTS_LIMIT = 10000;
    private static final int MAX_RAW_MAP_POINTS_LIMIT = 25000;

    private final GpsPointService gpsPointService;
    private final CurrentUserService currentUserService;
    private final PathSimplificationService pathSimplificationService;
    private final TimelineConfigurationProvider configurationProvider;
    private final GpsSourceService gpsSourceService;
    private final AsyncTimelineGenerationService asyncTimelineGenerationService;
    private final CoverageService coverageService;
    private final CoverageProcessingService coverageProcessingService;

    @Inject
    GeoPulseWorkloadMetrics workloadMetrics;

    @Inject
    CsvExportService csvExportService;

    @Inject
    public GpsPointResource(GpsPointService gpsPointService,
                            CurrentUserService currentUserService,
                            PathSimplificationService pathSimplificationService,
                            TimelineConfigurationProvider configurationProvider,
                            GpsSourceService gpsSourceService,
                            AsyncTimelineGenerationService asyncTimelineGenerationService,
                            CoverageService coverageService,
                            CoverageProcessingService coverageProcessingService) {
        this.gpsPointService = gpsPointService;
        this.currentUserService = currentUserService;
        this.pathSimplificationService = pathSimplificationService;
        this.configurationProvider = configurationProvider;
        this.gpsSourceService = gpsSourceService;
        this.asyncTimelineGenerationService = asyncTimelineGenerationService;
        this.coverageService = coverageService;
        this.coverageProcessingService = coverageProcessingService;
    }

    /**
     * Allows to store GPS points for a user sent by the mobile app.
     * This endpoint requires authentication.
     *
     * @param request Current locations from the mobile app.
     * @return The HTTP code, (200, 409, 500)
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"USER", "ADMIN"})
    public GpsIngestionResponse ingestMobileAppPoints(@Valid GpsPointsRetentionRequest request,
                                                      @RestHeader("X-Device-Id") String xDeviceId) {
        long started = System.nanoTime();
        var deviceId = xDeviceId == null ? "MOBILE APP" : xDeviceId;
        var points = request.getPoints() == null ? Collections.<GpsPointDTO>emptyList() : request.getPoints();

        try {
            UUID userId = currentUserService.getCurrentUserId();
            GpsSourceConfigEntity config = buildMobileAppDefaultConfig();

            var summary = gpsPointService.saveMobileAppGpsPoints(points, deviceId, userId, GpsSourceType.MOBILE_APP, config);
            if (summary != null) summary.logCompletion(GpsSourceType.MOBILE_APP, started);
            return GpsIngestionResponse.success();
        } catch (GpsCoordinateDuplicateException ex) {
            throw new GeoPulseException(GPS_POINT_DUPLICATE, "Duplicate point", ex);
        }
    }

    private GpsSourceConfigEntity buildMobileAppDefaultConfig() {
        GpsSourceConfigEntity config = new GpsSourceConfigEntity();
        config.setSourceType(GpsSourceType.MOBILE_APP);
        config.setActive(true);
        config.setFilterInaccurateData(gpsSourceService.isDefaultFilterInaccurateDataEnabled());
        config.setMaxAllowedAccuracy(gpsSourceService.getDefaultMaxAllowedAccuracy());
        config.setMaxAllowedSpeed(gpsSourceService.getDefaultMaxAllowedSpeed());
        config.setEnableDuplicateDetection(gpsSourceService.isDefaultDuplicateDetectionEnabled());
        config.setDuplicateDetectionThresholdMinutes(gpsSourceService.getDefaultDuplicateDetectionThresholdMinutes());
        return config;
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
    public GpsPointPathDTO getGpsPointPath(
            @QueryParam("from") String startTime,
            @QueryParam("to") String endTime,
            @QueryParam("simplify") @DefaultValue("true") boolean simplify) {
        UserEntity user = currentUserService.getCurrentUser();
        log.info("Received request to get GPS point path for user {} between {} and {}", user.getId(), startTime, endTime);

        try {
            Instant start = startTime != null ? Instant.parse(startTime) : Instant.EPOCH;
            Instant end = endTime != null ? Instant.parse(endTime) : Instant.now();
            GpsPointPathDTO path = gpsPointService.getGpsPointPath(user.getId(), start, end);
            TimelineConfig config = configurationProvider.getConfigurationForUser(user.getId());

            if (simplify && config.getPathSimplificationEnabled()) {
                path.setPoints(simplifyPath(user.getId(), start, end, path.getPoints(), config));
            }

            int gapThreshold = (config.getDataGapThresholdSeconds() != null && config.getDataGapThresholdSeconds() > 0)
                    ? config.getDataGapThresholdSeconds()
                    : 10800;
            path.setSegments(segmentPath(path.getPoints(), gapThreshold));

            return path;
        } catch (DateTimeParseException e) {
            throw new GeoPulseException(INVALID_GPS_QUERY, "Invalid time format. Use ISO-8601 format (e.g., 2023-01-01T00:00:00Z)", e);
        }
    }

    @GET
    @Path("/map")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"USER", "ADMIN"})
    public RawGpsPointMapResponseDTO getRawGpsMapPoints(
            @QueryParam("from") String startTime,
            @QueryParam("to") String endTime,
            @QueryParam("limit") @DefaultValue("" + DEFAULT_RAW_MAP_POINTS_LIMIT) int limit) {
        UUID userId = currentUserService.getCurrentUserId();

        try {
            if (limit < 1 || limit > MAX_RAW_MAP_POINTS_LIMIT) {
                throw new GeoPulseException(INVALID_LIMIT, "Limit must be between 1 and " + MAX_RAW_MAP_POINTS_LIMIT,
                        Map.of("min", 1, "max", MAX_RAW_MAP_POINTS_LIMIT));
            }

            Instant start = startTime != null ? Instant.parse(startTime) : Instant.EPOCH;
            Instant end = endTime != null ? Instant.parse(endTime) : Instant.now();
            RawGpsPointMapResponseDTO result = gpsPointService.getRawGpsMapPoints(userId, start, end, limit);
            return result;
        } catch (DateTimeParseException e) {
            throw new GeoPulseException(INVALID_GPS_QUERY, "Invalid time format. Use ISO-8601 format (e.g., 2023-01-01T00:00:00Z)", e);
        }
    }

    @GET
    @Path("/{pointId}/location")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"USER", "ADMIN"})
    public RawGpsPointLocationDTO resolveRawGpsPointLocation(@PathParam("pointId") Long pointId) {
        UUID userId = currentUserService.getCurrentUserId();

        try {
            RawGpsPointLocationDTO result = gpsPointService.resolveRawGpsPointLocation(userId, pointId);
            return result;
        } catch (NotFoundException e) {
            throw new GeoPulseException(GPS_POINT_NOT_FOUND, GPS_POINT_NOT_FOUND.title(), e);
        } catch (ForbiddenException e) {
            throw new GeoPulseException(GPS_POINT_ACCESS_DENIED, "Access denied", e);
        }
    }

    /**
     * Splits a flat list of GPS points into contiguous segments by breaking wherever
     * two consecutive points are separated by more than {@code gapThresholdSeconds}.
     * Each segment is rendered as a separate polyline on the map, preventing phantom
     * lines across unrecorded periods (e.g. between two separate GPX imports).
     */
    private List<List<GpsPoint>> segmentPath(List<? extends GpsPoint> points, int gapThresholdSeconds) {
        if (points == null || points.isEmpty()) return List.of();

        List<List<GpsPoint>> segments = new ArrayList<>();
        List<GpsPoint> current = new ArrayList<>();

        for (int i = 0; i < points.size(); i++) {
            GpsPoint p = points.get(i);
            if (i > 0) {
                GpsPoint prev = points.get(i - 1);
                if (prev.getTimestamp() != null && p.getTimestamp() != null) {
                    long gapSeconds = Duration.between(prev.getTimestamp(), p.getTimestamp()).getSeconds();
                    if (gapSeconds > gapThresholdSeconds) {
                        segments.add(current);
                        current = new ArrayList<>();
                    }
                }
            }
            current.add(p);
        }
        if (!current.isEmpty()) segments.add(current);
        return segments;
    }

    private List<? extends GpsPoint> simplifyPath(UUID userId, Instant start, Instant end, List<? extends GpsPoint> points, TimelineConfig config) {
        // Apply segment-aware GPS path simplification
        List<TimelineSegmentBoundary> segments = pathSimplificationService.getTimelineSegments(userId, start, end);

        List<? extends GpsPoint> simplifiedPath;
        if (segments.isEmpty()) {
            // Fallback to legacy simplification if no timeline segments found
            log.debug("No timeline segments found, falling back to legacy simplification");
            simplifiedPath = pathSimplificationService.simplify(points, config);
        } else {
            // Use segment-aware simplification with per-segment minimum point guarantees
            log.debug("Using segment-aware simplification with {} segments", segments.size());
            simplifiedPath = pathSimplificationService.simplifyWithSegments(points, segments, config);
        }
        return simplifiedPath;
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
    public GpsPointSummaryDTO getGpsPointSummary(
            @QueryParam("from") String startTime,
            @QueryParam("to") String endTime,
            @QueryParam("accuracyMin") Double accuracyMin,
            @QueryParam("accuracyMax") Double accuracyMax,
            @QueryParam("speedMin") Double speedMin,
            @QueryParam("speedMax") Double speedMax,
            @QueryParam("sourceTypes") String sourceTypes) {
        UserEntity user = currentUserService.getCurrentUser();
        log.info("Received request to get GPS point summary for user {} with timezone {}", user.getId(), user.getTimezone());

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

        return summary;
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
    public PageResponse<GpsPointDTO> getGpsPoints(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("50") int limit,
            @QueryParam("from") String startTime,
            @QueryParam("to") String endTime,
            @QueryParam("sortBy") @DefaultValue("timestamp") String sortBy,
            @QueryParam("sortDirection") @DefaultValue("desc") String sortOrder,
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
                throw new GeoPulseException(INVALID_PAGE, "Page number must be greater than 0", Map.of("min", 1));
            }
            if (limit < 1 || limit > 1000) {
                throw new GeoPulseException(INVALID_LIMIT, "Limit must be between 1 and 1000", Map.of("min", 1, "max", 1000));
            }

            // Validate sort order
            if (!sortOrder.equalsIgnoreCase("asc") && !sortOrder.equalsIgnoreCase("desc")) {
                throw new GeoPulseException(INVALID_GPS_QUERY, "Sort order must be 'asc' or 'desc'",
                        Map.of("sortOrder", sortOrder));
            }

            // Build filters
            GpsPointFilterDTO filters = buildFilters(startTime, endTime,
                    accuracyMin, accuracyMax, speedMin, speedMax, sourceTypes);

            return gpsPointService.getGpsPointsPageWithFilters(userId, filters, page, limit, sortBy, sortOrder);
        } catch (DateTimeParseException e) {
            throw new GeoPulseException(INVALID_GPS_QUERY, "Invalid date/time format", e);
        }
    }

    /**
     * Export GPS points as CSV with streaming to prevent OOM.
     * This endpoint requires authentication and supports all filters.
     *
     * @return CSV file with GPS points (streamed)
     */
    @GET
    @Path("/exports")
    @Produces("text/csv")
    @RolesAllowed({"USER", "ADMIN"})
    @APIResponse(responseCode = "200", description = "GPS points CSV export",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = SchemaType.STRING)))
    public Response exportGpsPoints(
            @QueryParam("from") String startTime,
            @QueryParam("to") String endTime,
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
                    startTime,
                    endTime,
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
                    throw new GeoPulseException(INVALID_GPS_QUERY, "Invalid GPS point IDs format", e);
                }
            }

            StreamingOutput stream = output -> csvExportService.generateCsvExport(
                    output, user.getId(), filters, user.getDistanceUnit());

            String filename = String.format("gps-points-export-%s.csv",
                    startTime != null && endTime != null ? startTime + "_" + endTime : "all");

            return Response.ok(stream)
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", "text/csv; charset=utf-8")
                    .build();
        } catch (DateTimeParseException e) {
            throw new GeoPulseException(INVALID_GPS_QUERY, "Invalid date/time format", e);
        }
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

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"USER", "ADMIN"})
    public GpsStatusDTO getGpsStatus() {
        UUID userId = currentUserService.getCurrentUserId();
        long startedAtNanos = workloadMetrics == null ? System.nanoTime() : workloadMetrics.start();
        String result = "success";
        log.info("Received request to get GPS status for user {}", userId);
        try {
            return gpsPointService.getGpsStatus(userId);
        } catch (Exception e) {
            result = "error";
            throw e;
        } finally {
            if (workloadMetrics != null) {
                workloadMetrics.recordTimer("geopulse.gps.ingest.duration", startedAtNanos,
                        "component", "gps",
                        "source", "STATUS",
                        "transport", "http",
                        "result", result);
            }
        }
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
    public GpsPointDTO updateGpsPoint(@PathParam("pointId") Long pointId, @Valid EditGpsPointDto editDto) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to update GPS point {} for user {}", pointId, userId);

        try {
            GpsPointDTO updatedPoint = gpsPointService.updateGpsPoint(pointId, editDto, userId);
            return updatedPoint;
        } catch (NotFoundException e) {
            throw new GeoPulseException(GPS_POINT_NOT_FOUND, "GPS point not found", e);
        } catch (ForbiddenException e) {
            throw new GeoPulseException(GPS_POINT_ACCESS_DENIED, "Access denied", e);
        }
    }

    /**
     * Delete ALL GPS points and timeline data for the authenticated user.
     * Uses efficient bulk SQL deletion - suitable for millions of records.
     * Also deletes timeline_stays, timeline_trips, and timeline_data_gaps.
     *
     * @return 200 OK if successful
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({"USER", "ADMIN"})
    public void deleteAllGpsData() {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to delete ALL GPS data for user {}", userId);

        gpsPointService.deleteAllGpsData(userId);
    }

    /**
     * Delete a GPS point.
     * This endpoint requires authentication.
     *
     * @param pointId The ID of the GPS point to delete
     * @return Delete result and background refresh scheduling status
     */
    @DELETE
    @Path("/{pointId}")
    @RolesAllowed({"USER", "ADMIN"})
    public GpsPointDeleteResponse deleteGpsPoint(@PathParam("pointId") Long pointId) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to delete GPS point {} for user {}", pointId, userId);

        try {
            GpsPointDeleteResult deleteResult = gpsPointService.deleteGpsPoint(pointId, userId);
            return buildDeleteResponse(userId, deleteResult);
        } catch (NotFoundException e) {
            throw new GeoPulseException(GPS_POINT_NOT_FOUND, "GPS point not found", e);
        } catch (ForbiddenException e) {
            throw new GeoPulseException(GPS_POINT_ACCESS_DENIED, "Access denied", e);
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
    public GpsPointDeleteResponse deleteGpsPoints(@Valid BulkDeleteGpsPointsDto bulkDeleteDto) {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to delete {} GPS points for user {}",
                bulkDeleteDto.getGpsPointIds().size(), userId);

        try {
            GpsPointDeleteResult deleteResult = gpsPointService.deleteGpsPoints(bulkDeleteDto.getGpsPointIds(), userId);
            return buildDeleteResponse(userId, deleteResult);
        } catch (ForbiddenException e) {
            throw new GeoPulseException(GPS_POINT_ACCESS_DENIED, "Access denied", e);
        }
    }

    private GpsPointDeleteResponse buildDeleteResponse(UUID userId, GpsPointDeleteResult deleteResult) {
        if (deleteResult.deletedCount() == 0) {
            return new GpsPointDeleteResponse(0, null, false, false);
        }

        AsyncTimelineGenerationService.TimelineSchedulingResult timelineResult =
                asyncTimelineGenerationService.scheduleTimelineRegenerationFromTimestamp(
                        userId,
                        deleteResult.earliestAffectedTimestamp());

        boolean coverageRebuildScheduled = scheduleCoverageRebuildIfEnabled(userId);

        return new GpsPointDeleteResponse(
                deleteResult.deletedCount(),
                timelineResult.jobId(),
                timelineResult.scheduled(),
                coverageRebuildScheduled);
    }

    private boolean scheduleCoverageRebuildIfEnabled(UUID userId) {
        try {
            CoverageStatus status = coverageService.getCoverageStatus(userId);
            if (!status.userEnabled()) {
                return false;
            }

            CoverageProcessingService.CoverageSchedulingResult result =
                    coverageProcessingService.requestFullRecalculationAsync(userId);
            return result.scheduled();
        } catch (Exception e) {
            log.warn("Failed to schedule coverage rebuild after GPS deletion for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    @GET
    @Path("/latest")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"USER", "ADMIN"})
    @APIResponseSchema(value = GpsPointDTO.class, responseCode = "200",
            responseDescription = "Last known GPS position")
    public Response getLastKnownPosition() {
        UUID userId = currentUserService.getCurrentUserId();
        log.info("Received request to get last known position for user {}", userId);
        Optional<GpsPointDTO> lastPosition = gpsPointService.getLastKnownPosition(userId);
        return Response.ok(lastPosition.orElse(null)).build();
    }
}
