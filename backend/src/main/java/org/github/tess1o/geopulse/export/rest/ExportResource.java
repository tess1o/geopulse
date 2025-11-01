package org.github.tess1o.geopulse.export.rest;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.export.model.CreateExportRequest;
import org.github.tess1o.geopulse.export.model.DebugExportRequest;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.export.model.ExportJobResponse;
import org.github.tess1o.geopulse.export.service.DebugExportService;
import org.github.tess1o.geopulse.export.service.ExportJobManager;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/export")
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

    @POST
    @Path("/owntracks/create")
    public Response createOwnTracksExport(CreateExportRequest request) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            // Validate request
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

            ExportJob job = exportJobManager.createOwnTracksExportJob(userId, request.getDateRange());

            ExportJobResponse response = new ExportJobResponse();
            response.setSuccess(true);
            response.setExportJobId(job.getJobId());
            response.setStatus(job.getStatus().name().toLowerCase());
            response.setMessage("OwnTracks export job created successfully");
            response.setEstimatedCompletionTime(Instant.now().plus(5, ChronoUnit.MINUTES));

            return Response.ok(response).build();

        } catch (IllegalStateException e) {
            return Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .entity(createErrorResponse("RATE_LIMIT_EXCEEDED", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create OwnTracks export job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("INTERNAL_ERROR", "Failed to create OwnTracks export job"))
                    .build();
        }
    }

    @POST
    @Path("/geojson/create")
    public Response createGeoJsonExport(CreateExportRequest request) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            // Validate request
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

            ExportJob job = exportJobManager.createGeoJsonExportJob(userId, request.getDateRange());

            ExportJobResponse response = new ExportJobResponse();
            response.setSuccess(true);
            response.setExportJobId(job.getJobId());
            response.setStatus(job.getStatus().name().toLowerCase());
            response.setMessage("GeoJSON export job created successfully");
            response.setEstimatedCompletionTime(Instant.now().plus(5, ChronoUnit.MINUTES));

            return Response.ok(response).build();

        } catch (IllegalStateException e) {
            return Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .entity(createErrorResponse("RATE_LIMIT_EXCEEDED", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create GeoJSON export job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("INTERNAL_ERROR", "Failed to create GeoJSON export job"))
                    .build();
        }
    }

    @POST
    @Path("/gpx/create")
    public Response createGpxExport(CreateExportRequest request) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            // Validate request
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

            ExportJob job = exportJobManager.createGpxExportJob(userId, request.getDateRange(), zipPerTrip, zipGroupBy);

            ExportJobResponse response = new ExportJobResponse();
            response.setSuccess(true);
            response.setExportJobId(job.getJobId());
            response.setStatus(job.getStatus().name().toLowerCase());
            response.setMessage("GPX export job created successfully");
            response.setEstimatedCompletionTime(Instant.now().plus(5, ChronoUnit.MINUTES));

            return Response.ok(response).build();

        } catch (IllegalStateException e) {
            return Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .entity(createErrorResponse("RATE_LIMIT_EXCEEDED", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create GPX export job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("INTERNAL_ERROR", "Failed to create GPX export job"))
                    .build();
        }
    }

    @GET
    @Path("/gpx/trip/{tripId}")
    @Produces("application/gpx+xml")
    public Response exportSingleTrip(@PathParam("tripId") Long tripId) {
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
    @Path("/gpx/stay/{stayId}")
    @Produces("application/gpx+xml")
    public Response exportSingleStay(@PathParam("stayId") Long stayId) {
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
    @Path("/create")
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
    @Path("/status/{exportJobId}")
    public Response getExportStatus(@PathParam("exportJobId") UUID exportJobId) {
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
    @Path("/download/{exportJobId}")
    @Produces({"application/zip", "application/json", "application/gpx+xml"})
    public Response downloadExport(@PathParam("exportJobId") UUID exportJobId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            ExportJob job = exportJobManager.getExportJob(exportJobId, userId);

            if (job == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Export job not found")
                        .build();
            }

            if (!job.getStatus().name().equals("COMPLETED") ||
                (job.getZipData() == null && job.getJsonData() == null)) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Export not ready for download")
                        .build();
            }

            // Check if export has expired (24 hours)
            if (job.getCreatedAt().plus(24, ChronoUnit.HOURS).isBefore(Instant.now())) {
                return Response.status(Response.Status.GONE)
                        .entity("Export has expired")
                        .build();
            }

            // Determine format and return appropriate response
            if ("owntracks".equals(job.getFormat()) && job.getJsonData() != null) {
                // Return JSON for OwnTracks format
                String filename = String.format("owntracks-export-%s-%d.json",
                        userId.toString().substring(0, 8),
                        job.getCreatedAt().getEpochSecond());

                return Response.ok(job.getJsonData())
                        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                        .header("Content-Type", "application/json")
                        .header("Content-Length", job.getJsonData().length)
                        .build();
            } else if ("geojson".equals(job.getFormat()) && job.getJsonData() != null) {
                // Return JSON for GeoJSON format
                String filename = String.format("geopulse-export-%s-%d.geojson",
                        userId.toString().substring(0, 8),
                        job.getCreatedAt().getEpochSecond());

                return Response.ok(job.getJsonData())
                        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                        .header("Content-Type", "application/geo+json")
                        .header("Content-Length", job.getJsonData().length)
                        .build();
            } else if ("gpx".equals(job.getFormat())) {
                // Return GPX format (could be single file or zip)
                if (job.getZipData() != null) {
                    // Return ZIP with multiple GPX files
                    String filename = String.format("geopulse-gpx-export-%s-%d.zip",
                            userId.toString().substring(0, 8),
                            job.getCreatedAt().getEpochSecond());

                    return Response.ok(job.getZipData())
                            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                            .header("Content-Type", "application/zip")
                            .header("Content-Length", job.getZipData().length)
                            .build();
                } else if (job.getJsonData() != null) {
                    // Return single GPX file (stored in jsonData for consistency)
                    String filename = String.format("geopulse-export-%s-%d.gpx",
                            userId.toString().substring(0, 8),
                            job.getCreatedAt().getEpochSecond());

                    return Response.ok(job.getJsonData())
                            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                            .header("Content-Type", "application/gpx+xml")
                            .header("Content-Length", job.getJsonData().length)
                            .build();
                } else {
                    return Response.status(Response.Status.NOT_FOUND)
                            .entity("GPX export data not available")
                            .build();
                }
            } else if (job.getZipData() != null) {
                // Return ZIP for GeoPulse format
                String filename = String.format("geopulse-export-%s-%d.zip",
                        userId.toString().substring(0, 8),
                        job.getCreatedAt().getEpochSecond());

                return Response.ok(job.getZipData())
                        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                        .header("Content-Type", "application/zip")
                        .header("Content-Length", job.getZipData().length)
                        .build();
            } else {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Export data not available")
                        .build();
            }

        } catch (Exception e) {
            log.error("Failed to download export", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to download export")
                    .build();
        }
    }

    @GET
    @Path("/jobs")
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
    @Path("/jobs/{exportJobId}")
    public Response deleteExportJob(@PathParam("exportJobId") UUID exportJobId) {
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
    @Path("/debug/create")
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