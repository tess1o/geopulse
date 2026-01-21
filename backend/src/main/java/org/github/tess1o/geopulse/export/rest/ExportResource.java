package org.github.tess1o.geopulse.export.rest;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.export.model.CreateExportRequest;
import org.github.tess1o.geopulse.export.model.DebugExportRequest;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.export.model.ExportJobResponse;
import org.github.tess1o.geopulse.export.service.DebugExportService;
import org.github.tess1o.geopulse.export.service.ExportJobManager;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@jakarta.ws.rs.Path("/api/export")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
public class ExportResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ExportJobManager exportJobManager;

    @Inject
    DebugExportService debugExportService;

    @Inject
    SystemSettingsService settingsService;

    @POST
    @jakarta.ws.rs.Path("/owntracks/create")
    public Response createOwnTracksExport(CreateExportRequest request) {
        try {
            var validationError = validateDateRange(request.getDateRange());
            if (validationError.isPresent()) {
                return validationError.get();
            }

            UUID userId = currentUserService.getCurrentUserId();
            ExportJob job = exportJobManager.createOwnTracksExportJob(userId, request.getDateRange());
            return createExportJobSuccessResponse(job, "OwnTracks");

        } catch (IllegalStateException e) {
            return handleTooManyRequests(e);
        } catch (Exception e) {
            return handleExportCreationError(e, "OwnTracks");
        }
    }

    @POST
    @jakarta.ws.rs.Path("/geojson/create")
    public Response createGeoJsonExport(CreateExportRequest request) {
        try {
            var validationError = validateDateRange(request.getDateRange());
            if (validationError.isPresent()) {
                return validationError.get();
            }

            UUID userId = currentUserService.getCurrentUserId();
            ExportJob job = exportJobManager.createGeoJsonExportJob(userId, request.getDateRange());
            return createExportJobSuccessResponse(job, "GeoJSON");

        } catch (IllegalStateException e) {
            return handleTooManyRequests(e);
        } catch (Exception e) {
            return handleExportCreationError(e, "GeoJSON");
        }
    }

    @POST
    @jakarta.ws.rs.Path("/gpx/create")
    public Response createGpxExport(CreateExportRequest request) {
        try {
            var validationError = validateDateRange(request.getDateRange());
            if (validationError.isPresent()) {
                return validationError.get();
            }

            // Get zipPerTrip option from request options
            boolean zipPerTrip = false;
            String zipGroupBy = "individual"; // default
            if (request.getOptions() != null) {
                if (request.getOptions().containsKey("zipPerTrip")) {
                    zipPerTrip = Boolean.parseBoolean(request.getOptions().get("zipPerTrip").toString());
                }
                if (request.getOptions().containsKey("zipGroupBy")) {
                    zipGroupBy = request.getOptions().get("zipGroupBy").toString();
                }
            }

            UUID userId = currentUserService.getCurrentUserId();
            ExportJob job = exportJobManager.createGpxExportJob(userId, request.getDateRange(), zipPerTrip, zipGroupBy);
            return createExportJobSuccessResponse(job, "GPX");

        } catch (IllegalStateException e) {
            return handleTooManyRequests(e);
        } catch (Exception e) {
            return handleExportCreationError(e, "GPX");
        }
    }

    @POST
    @jakarta.ws.rs.Path("/csv/create")
    public Response createCsvExport(CreateExportRequest request) {
        try {
            var validationError = validateDateRange(request.getDateRange());
            if (validationError.isPresent()) {
                return validationError.get();
            }

            UUID userId = currentUserService.getCurrentUserId();
            ExportJob job = exportJobManager.createCsvExportJob(userId, request.getDateRange());
            return createExportJobSuccessResponse(job, "CSV");

        } catch (IllegalStateException e) {
            return handleTooManyRequests(e);
        } catch (Exception e) {
            return handleExportCreationError(e, "CSV");
        }
    }

    @GET
    @jakarta.ws.rs.Path("/gpx/trip/{tripId}")
    @Produces("application/gpx+xml")
    public Response exportSingleTrip(@jakarta.ws.rs.PathParam("tripId") Long tripId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            byte[] gpxData = exportJobManager.exportSingleTrip(userId, tripId);

            String filename = String.format("trip-%d-%d.gpx", tripId, Instant.now().getEpochSecond());

            return Response.ok(gpxData)
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", "application/gpx+xml")
                    .header("Content-Length", gpxData.length)
                    .build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(createErrorResponse("NOT_FOUND", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to export trip as GPX", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("INTERNAL_ERROR", "Failed to export trip"))
                    .build();
        }
    }

    @GET
    @jakarta.ws.rs.Path("/gpx/stay/{stayId}")
    @Produces("application/gpx+xml")
    public Response exportSingleStay(@jakarta.ws.rs.PathParam("stayId") Long stayId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            byte[] gpxData = exportJobManager.exportSingleStay(userId, stayId);

            String filename = String.format("stay-%d-%d.gpx", stayId, Instant.now().getEpochSecond());

            return Response.ok(gpxData)
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", "application/gpx+xml")
                    .header("Content-Length", gpxData.length)
                    .build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(createErrorResponse("NOT_FOUND", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to export stay as GPX", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("INTERNAL_ERROR", "Failed to export stay"))
                    .build();
        }
    }

    @POST
    @jakarta.ws.rs.Path("/create")
    public Response createExport(CreateExportRequest request) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            // Validate request
            if (request.getDataTypes() == null || request.getDataTypes().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("INVALID_REQUEST", "Data types are required"))
                        .build();
            }

            if (request.getDateRange() == null ||
                    request.getDateRange().getStartDate() == null ||
                    request.getDateRange().getEndDate() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("INVALID_REQUEST", "Date range is required"))
                        .build();
            }

            if (request.getDateRange().getStartDate().isAfter(Instant.now())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("INVALID_REQUEST", "Start date cannot be in the future"))
                        .build();
            }

            if (request.getDateRange().getStartDate().isAfter(request.getDateRange().getEndDate())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("INVALID_REQUEST", "Start date must be before end date"))
                        .build();
            }

            ExportJob job = exportJobManager.createExportJob(userId, request.getDataTypes(),
                    request.getDateRange(), request.getFormat());

            ExportJobResponse response = new ExportJobResponse();
            response.setSuccess(true);
            response.setExportJobId(job.getJobId());
            response.setStatus(job.getStatus().name().toLowerCase());
            response.setMessage("Export job created successfully");
            response.setEstimatedCompletionTime(Instant.now().plus(5, ChronoUnit.MINUTES));

            return Response.ok(response).build();

        } catch (IllegalStateException e) {
            return Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .entity(createErrorResponse("RATE_LIMIT_EXCEEDED", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create export job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("INTERNAL_ERROR", "Failed to create export job"))
                    .build();
        }
    }

    @GET
    @jakarta.ws.rs.Path("/status/{exportJobId}")
    public Response getExportStatus(@jakarta.ws.rs.PathParam("exportJobId") UUID exportJobId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            ExportJob job = exportJobManager.getExportJob(exportJobId, userId);

            if (job == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(createErrorResponse("EXPORT_NOT_FOUND", "Export job not found"))
                        .build();
            }

            ExportJobResponse response = new ExportJobResponse();
            response.setSuccess(true);
            response.setExportJobId(job.getJobId());
            response.setStatus(job.getStatus().name().toLowerCase());
            response.setProgress(job.getProgress());
            response.setProgressMessage(job.getProgressMessage());
            response.setCreatedAt(job.getCreatedAt());
            response.setCompletedAt(job.getCompletedAt());
            response.setDataTypes(job.getDataTypes());
            response.setDateRange(job.getDateRange());
            response.setError(job.getError());

            if (job.getStatus().name().equals("COMPLETED")) {
                response.setDownloadUrl("/api/export/download/" + job.getJobId());
                response.setExpiresAt(job.getCreatedAt().plus(24, ChronoUnit.HOURS));
                response.setFileSizeBytes(job.getFileSizeBytes());
            }

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Failed to get export status", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("INTERNAL_ERROR", "Failed to get export status"))
                    .build();
        }
    }

    @GET
    @jakarta.ws.rs.Path("/csv/template")
    @Produces("text/csv")
    public Response downloadCsvTemplate() {
        try {
            // Generate sample CSV with proper format
            StringBuilder csvTemplate = new StringBuilder();
            csvTemplate.append("timestamp,latitude,longitude,accuracy,velocity,altitude,battery,device_id,source_type\n");
            csvTemplate.append("2024-01-15T10:30:00Z,37.7749,-122.4194,10.5,5.2,100.0,85.0,device123,CSV\n");
            csvTemplate.append("2024-01-15T10:35:00Z,37.7750,-122.4195,8.3,12.8,105.2,84.8,,CSV\n");
            csvTemplate.append("2024-01-15T10:40:00Z,37.7751,-122.4196,,15.5,,,device789,GPX\n");

            byte[] csvData = csvTemplate.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String filename = "geopulse-gps-import-template.csv";

            return Response.ok(csvData)
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", "text/csv; charset=utf-8")
                    .header("Content-Length", csvData.length)
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate CSV template", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("INTERNAL_ERROR", "Failed to generate CSV template"))
                    .build();
        }
    }

    @GET
    @jakarta.ws.rs.Path("/download/{exportJobId}")
    @Produces({"application/zip", "application/json", "application/gpx+xml", "text/csv"})
    public Response downloadExport(@jakarta.ws.rs.PathParam("exportJobId") UUID exportJobId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            ExportJob job = exportJobManager.getExportJob(exportJobId, userId);

            if (job == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Export job not found")
                        .build();
            }

            if (!job.getStatus().name().equals("COMPLETED") || job.getTempFilePath() == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Export not ready for download")
                        .build();
            }

            // Check if export has expired (configurable)
            int expiryHours = settingsService.getInteger("export.job-expiry-hours");
            if (job.getCreatedAt().plus(expiryHours, ChronoUnit.HOURS).isBefore(Instant.now())) {
                return Response.status(Response.Status.GONE)
                        .entity("Export has expired")
                        .build();
            }

            // Verify temp file exists
            Path exportFile = Paths.get(job.getTempFilePath());
            if (!Files.exists(exportFile)) {
                log.error("Export file not found on disk: {}", job.getTempFilePath());
                return Response.status(Response.Status.GONE)
                        .entity("Export file not found")
                        .build();
            }

            // Generate filename based on format
            String filename = generateFilename(job, userId);

            // Stream file content directly without loading into memory
            StreamingOutput stream = output -> {
                try (InputStream input = Files.newInputStream(exportFile)) {
                    input.transferTo(output);
                }
            };

            log.info("Streaming export download for job {} - {} bytes from {}",
                    job.getJobId(), job.getFileSizeBytes(), exportFile.getFileName());

            return Response.ok(stream)
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", job.getContentType())
                    .header("Content-Length", job.getFileSizeBytes())
                    .build();

        } catch (Exception e) {
            log.error("Failed to download export", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to download export")
                    .build();
        }
    }

    /**
     * Generate download filename based on export format and job metadata.
     */
    private String generateFilename(ExportJob job, UUID userId) {
        String userPrefix = userId.toString().substring(0, 8);
        long timestamp = job.getCreatedAt().getEpochSecond();
        String extension = job.getFileExtension() != null ? job.getFileExtension() : ".dat";

        // Remove leading dot if present for clean filename construction
        if (extension.startsWith(".")) {
            extension = extension.substring(1);
        }

        return switch (job.getFormat()) {
            case "owntracks" -> String.format("owntracks-export-%s-%d.%s", userPrefix, timestamp, extension);
            case "geojson" -> String.format("geopulse-export-%s-%d.%s", userPrefix, timestamp, extension);
            case "gpx" -> String.format("geopulse-gpx-export-%s-%d.%s", userPrefix, timestamp, extension);
            case "csv" -> String.format("geopulse-export-%s-%d.%s", userPrefix, timestamp, extension);
            default -> String.format("geopulse-export-%s-%d.%s", userPrefix, timestamp, extension);
        };
    }

    @GET
    @jakarta.ws.rs.Path("/jobs")
    public Response listExportJobs(@QueryParam("limit") @DefaultValue("10") int limit,
                                   @QueryParam("offset") @DefaultValue("0") int offset) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            // Validate parameters
            if (limit < 1 || limit > 50) {
                limit = 10;
            }
            if (offset < 0) {
                offset = 0;
            }

            List<ExportJob> jobs = exportJobManager.getUserExportJobs(userId, limit, offset);

            List<ExportJobResponse> jobResponses = jobs.stream().map(job -> {
                ExportJobResponse response = new ExportJobResponse();
                response.setExportJobId(job.getJobId());
                response.setStatus(job.getStatus().name().toLowerCase());
                response.setCreatedAt(job.getCreatedAt());
                response.setCompletedAt(job.getCompletedAt());
                response.setDataTypes(job.getDataTypes());
                response.setFileSizeBytes(job.getFileSizeBytes());

                if (job.getStatus().name().equals("COMPLETED")) {
                    response.setExpiresAt(job.getCreatedAt().plus(24, ChronoUnit.HOURS));
                }

                return response;
            }).collect(Collectors.toList());

            return Response.ok(new ListExportJobsResponse(true, jobResponses, jobs.size(), limit, offset)).build();

        } catch (Exception e) {
            log.error("Failed to list export jobs", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("INTERNAL_ERROR", "Failed to list export jobs"))
                    .build();
        }
    }

    @DELETE
    @jakarta.ws.rs.Path("/jobs/{exportJobId}")
    public Response deleteExportJob(@jakarta.ws.rs.PathParam("exportJobId") UUID exportJobId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            boolean deleted = exportJobManager.deleteExportJob(exportJobId, userId);

            if (!deleted) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(createErrorResponse("EXPORT_NOT_FOUND", "Export job not found"))
                        .build();
            }

            return Response.ok(ApiResponse.success("Export job deleted successfully")).build();

        } catch (Exception e) {
            log.error("Failed to delete export job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("INTERNAL_ERROR", "Failed to delete export job"))
                    .build();
        }
    }

    @POST
    @jakarta.ws.rs.Path("/debug/create")
    public Response createDebugExport(DebugExportRequest request) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            // Validate request
            if (request.getStartDate() == null || request.getEndDate() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("INVALID_REQUEST", "Start date and end date are required"))
                        .build();
            }

            if (request.getStartDate().isAfter(Instant.now())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("INVALID_REQUEST", "Start date cannot be in the future"))
                        .build();
            }

            if (request.getStartDate().isAfter(request.getEndDate())) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("INVALID_REQUEST", "Start date must be before end date"))
                        .build();
            }

            if (request.getLatitudeShift() == null || request.getLongitudeShift() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("INVALID_REQUEST", "Latitude and longitude shift are required"))
                        .build();
            }

            log.info("Creating debug export for user {} from {} to {}",
                    userId, request.getStartDate(), request.getEndDate());

            // Generate debug export synchronously (returns immediately with data)
            byte[] exportData = debugExportService.generateDebugExport(userId, request);

            // Return the ZIP file directly
            return Response.ok(exportData)
                    .header("Content-Type", "application/zip")
                    .header("Content-Disposition",
                            "attachment; filename=\"geopulse-debug-" +
                            userId + "-" + Instant.now().getEpochSecond() + ".zip\"")
                    .build();

        } catch (Exception e) {
            log.error("Failed to create debug export", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("INTERNAL_ERROR", "Failed to create debug export: " + e.getMessage()))
                    .build();
        }
    }

    // Helper method to create error responses with custom format
    private Map<String, Object> createErrorResponse(String code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);

        return response;
    }

    /**
     * Validate date range from export request.
     * @return Optional with error Response if invalid, empty if valid
     */
    private java.util.Optional<Response> validateDateRange(org.github.tess1o.geopulse.export.model.ExportDateRange dateRange) {
        if (dateRange == null ||
                dateRange.getStartDate() == null ||
                dateRange.getEndDate() == null) {
            return java.util.Optional.of(Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("INVALID_REQUEST", "Date range is required"))
                    .build());
        }

        if (dateRange.getStartDate().isAfter(Instant.now())) {
            return java.util.Optional.of(Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("INVALID_REQUEST", "Start date cannot be in the future"))
                    .build());
        }

        if (dateRange.getStartDate().isAfter(dateRange.getEndDate())) {
            return java.util.Optional.of(Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("INVALID_REQUEST", "Start date must be before end date"))
                    .build());
        }

        return java.util.Optional.empty();
    }

    /**
     * Create a success response for export job creation.
     */
    private Response createExportJobSuccessResponse(ExportJob job, String formatName) {
        ExportJobResponse response = new ExportJobResponse();
        response.setSuccess(true);
        response.setExportJobId(job.getJobId());
        response.setStatus(job.getStatus().name().toLowerCase());
        response.setMessage(formatName + " export job created successfully");
        response.setEstimatedCompletionTime(Instant.now().plus(5, ChronoUnit.MINUTES));
        return Response.ok(response).build();
    }

    /**
     * Handle rate limit exceeded (too many requests).
     */
    private Response handleTooManyRequests(IllegalStateException e) {
        return Response.status(Response.Status.TOO_MANY_REQUESTS)
                .entity(createErrorResponse("RATE_LIMIT_EXCEEDED", e.getMessage()))
                .build();
    }

    /**
     * Handle internal server error during export creation.
     */
    private Response handleExportCreationError(Exception e, String formatName) {
        log.error("Failed to create {} export job", formatName, e);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(createErrorResponse("INTERNAL_ERROR", "Failed to create " + formatName + " export job"))
                .build();
    }

    // Inner class for list response
    public static class ListExportJobsResponse {
        public boolean success;
        public List<ExportJobResponse> jobs;
        public int total;
        public int limit;
        public int offset;

        public ListExportJobsResponse(boolean success, List<ExportJobResponse> jobs, int total, int limit, int offset) {
            this.success = success;
            this.jobs = jobs;
            this.total = total;
            this.limit = limit;
            this.offset = offset;
        }
    }
}