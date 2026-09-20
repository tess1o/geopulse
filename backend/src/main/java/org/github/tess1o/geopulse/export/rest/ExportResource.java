package org.github.tess1o.geopulse.export.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.export.model.CreateExportRequest;
import org.github.tess1o.geopulse.export.model.DebugExportRequest;
import org.github.tess1o.geopulse.export.model.ExportDateRange;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.export.model.ExportJobResponse;
import org.github.tess1o.geopulse.export.service.DebugExportService;
import org.github.tess1o.geopulse.export.service.ExportJobManager;
import org.github.tess1o.geopulse.shared.api.SliceResponse;
import org.github.tess1o.geopulse.shared.api.MessageDescriptor;
import org.jboss.resteasy.reactive.RestResponse;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@Path("/exports")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = "User: Import and Export", description = "Create, monitor, download, and delete export jobs.")
public class ExportResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ExportJobManager exportJobManager;

    @Inject
    DebugExportService debugExportService;

    @Inject
    SystemSettingsService settingsService;

    @GET
    @Path("/trips/{tripId}/gpx")
    @Produces({"application/gpx+xml", "application/problem+json"})
    @APIResponse(responseCode = "200", description = "Trip exported as GPX",
            content = @Content(mediaType = "application/gpx+xml",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    @APIResponse(responseCode = "404", description = "Trip not found")
    public Response exportSingleTrip(@PathParam("tripId") Long tripId) throws Exception {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            byte[] data = exportJobManager.exportSingleTrip(userId, tripId);
            return download(data, "application/gpx+xml",
                    "trip-%d-%d.gpx".formatted(tripId, Instant.now().getEpochSecond()));
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(TRIP_NOT_FOUND, TRIP_NOT_FOUND.title(), exception);
        }
    }

    @GET
    @Path("/stays/{stayId}/gpx")
    @Produces({"application/gpx+xml", "application/problem+json"})
    @APIResponse(responseCode = "200", description = "Stay exported as GPX",
            content = @Content(mediaType = "application/gpx+xml",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    @APIResponse(responseCode = "404", description = "Stay not found")
    public Response exportSingleStay(@PathParam("stayId") Long stayId) throws Exception {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            byte[] data = exportJobManager.exportSingleStay(userId, stayId);
            return download(data, "application/gpx+xml",
                    "stay-%d-%d.gpx".formatted(stayId, Instant.now().getEpochSecond()));
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(STAY_NOT_FOUND, STAY_NOT_FOUND.title(), exception);
        }
    }

    @POST
    @APIResponse(responseCode = "200", description = "Export job created")
    @APIResponse(responseCode = "400", description = "Invalid export request")
    @APIResponse(responseCode = "429", description = "Too many active export jobs")
    public ExportJobResponse createExport(CreateExportRequest request) {
        if (request == null || request.getDataTypes() == null || request.getDataTypes().isEmpty()) {
            throw new GeoPulseException(INVALID_EXPORT_REQUEST, "Data types are required",
                    Map.of("field", "dataTypes"));
        }
        validateDateRange(request.getDateRange());
        if (request.getFormat() == null || request.getFormat().isBlank()) {
            request.setFormat("geopulse");
        }

        try {
            UUID userId = currentUserService.getCurrentUserId();
            return toResponse(exportJobManager.createExportJob(userId, request.getDataTypes(),
                    request.getDateRange(), request.getFormat(), request.getOptions()));
        } catch (IllegalStateException exception) {
            throw new GeoPulseException(RATE_LIMIT_EXCEEDED, RATE_LIMIT_EXCEEDED.title(), exception);
        }
    }

    @GET
    @Path("/{exportJobId}")
    @APIResponse(responseCode = "200", description = "Export job status")
    @APIResponse(responseCode = "404", description = "Export job not found")
    public ExportJobResponse getExportStatus(@PathParam("exportJobId") UUID exportJobId) {
        ExportJob job = exportJobManager.getExportJob(exportJobId, currentUserService.getCurrentUserId());
        if (job == null) {
            throw new GeoPulseException(EXPORT_NOT_FOUND, "Export job not found");
        }
        return toResponse(job);
    }

    @GET
    @Path("/csv-template")
    @Produces({"text/csv", "application/problem+json"})
    @APIResponse(responseCode = "200", description = "CSV import template",
            content = @Content(mediaType = "text/csv",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    public Response downloadCsvTemplate() {
        String csv = "timestamp,latitude,longitude,accuracy,velocity,altitude,battery,device_id,source_type\n"
                + "2024-01-15T10:30:00Z,37.7749,-122.4194,10.5,5.2,100.0,85.0,device123,CSV\n"
                + "2024-01-15T10:35:00Z,37.7750,-122.4195,8.3,12.8,105.2,84.8,,CSV\n"
                + "2024-01-15T10:40:00Z,37.7751,-122.4196,,15.5,,,device789,GPX\n";
        return download(csv.getBytes(StandardCharsets.UTF_8), "text/csv; charset=utf-8",
                "geopulse-gps-import-template.csv");
    }

    @GET
    @Path("/{exportJobId}/content")
    @Produces({"application/zip", MediaType.APPLICATION_JSON, "application/gpx+xml", "text/csv",
            "application/problem+json"})
    @APIResponse(responseCode = "200", description = "Export file",
            content = {
                    @Content(mediaType = "application/zip",
                            schema = @Schema(type = SchemaType.STRING, format = "binary")),
                    @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(type = SchemaType.STRING, format = "binary")),
                    @Content(mediaType = "application/gpx+xml",
                            schema = @Schema(type = SchemaType.STRING, format = "binary")),
                    @Content(mediaType = "text/csv",
                            schema = @Schema(type = SchemaType.STRING, format = "binary"))
            })
    @APIResponse(responseCode = "404", description = "Export job not found")
    @APIResponse(responseCode = "409", description = "Export job is not ready")
    @APIResponse(responseCode = "410", description = "Export expired or file missing")
    public Response downloadExport(@PathParam("exportJobId") UUID exportJobId) {
        UUID userId = currentUserService.getCurrentUserId();
        ExportJob job = exportJobManager.getExportJob(exportJobId, userId);
        if (job == null) {
            throw new GeoPulseException(EXPORT_NOT_FOUND, "Export job not found");
        }
        if (!"COMPLETED".equals(job.getStatus().name()) || job.getTempFilePath() == null) {
            throw new GeoPulseException(EXPORT_NOT_READY, "Export is not ready for download");
        }

        int expiryHours = settingsService.getInteger("export.job-expiry-hours");
        if (job.getCreatedAt().plus(expiryHours, ChronoUnit.HOURS).isBefore(Instant.now())) {
            throw new GeoPulseException(EXPORT_EXPIRED, "Export has expired");
        }

        java.nio.file.Path exportFile = Paths.get(job.getTempFilePath());
        if (!Files.exists(exportFile)) {
            throw new GeoPulseException(EXPORT_FILE_MISSING, "Export file not found");
        }

        StreamingOutput stream = output -> {
            try (InputStream input = Files.newInputStream(exportFile)) {
                input.transferTo(output);
            }
        };
        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"" + generateFilename(job, userId) + "\"")
                .header("Content-Type", job.getContentType())
                .header("Content-Length", job.getFileSizeBytes())
                .build();
    }

    @GET
    @APIResponse(responseCode = "200", description = "Export jobs")
    public SliceResponse<ExportJobResponse> listExportJobs(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size) {
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.min(Math.max(size, 1), 50);
        List<ExportJob> jobs = exportJobManager.getUserExportJobs(
                currentUserService.getCurrentUserId(), normalizedSize + 1, normalizedPage * normalizedSize);
        boolean hasNext = jobs.size() > normalizedSize;
        List<ExportJobResponse> items = jobs.stream()
                .limit(normalizedSize)
                .map(this::toResponse)
                .toList();
        return new SliceResponse<>(items, normalizedPage, normalizedSize, hasNext);
    }

    @DELETE
    @Path("/{exportJobId}")
    @APIResponse(responseCode = "204", description = "Export job deleted")
    @APIResponse(responseCode = "404", description = "Export job not found")
    public RestResponse<Void> deleteExportJob(@PathParam("exportJobId") UUID exportJobId) {
        if (!exportJobManager.deleteExportJob(exportJobId, currentUserService.getCurrentUserId())) {
            throw new GeoPulseException(EXPORT_NOT_FOUND, "Export job not found");
        }
        return RestResponse.noContent();
    }

    @POST
    @Path("/debug")
    @Produces({"application/zip", "application/problem+json"})
    @APIResponse(responseCode = "200", description = "Debug export archive",
            content = @Content(mediaType = "application/zip",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    @APIResponse(responseCode = "400", description = "Invalid debug export request")
    public Response createDebugExport(DebugExportRequest request) throws Exception {
        validateDebugRequest(request);
        UUID userId = currentUserService.getCurrentUserId();
        byte[] data = debugExportService.generateDebugExport(userId, request);
        return download(data, "application/zip",
                "geopulse-debug-%s-%d.zip".formatted(userId, Instant.now().getEpochSecond()));
    }

    private void validateDateRange(ExportDateRange dateRange) {
        if (dateRange == null || dateRange.getStartDate() == null || dateRange.getEndDate() == null) {
            throw new GeoPulseException(INVALID_DATE_RANGE, "Date range is required");
        }
        if (dateRange.getStartDate().isAfter(Instant.now())) {
            throw new GeoPulseException(INVALID_DATE_RANGE, "Start date cannot be in the future");
        }
        if (dateRange.getStartDate().isAfter(dateRange.getEndDate())) {
            throw new GeoPulseException(INVALID_DATE_RANGE, "Start date must be before end date");
        }
    }

    private void validateDebugRequest(DebugExportRequest request) {
        if (request == null || request.getStartDate() == null || request.getEndDate() == null) {
            throw new GeoPulseException(INVALID_DEBUG_EXPORT_REQUEST, "Start date and end date are required");
        }
        if (request.getStartDate().isAfter(Instant.now())) {
            throw new GeoPulseException(INVALID_DEBUG_EXPORT_REQUEST, "Start date cannot be in the future");
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new GeoPulseException(INVALID_DEBUG_EXPORT_REQUEST, "Start date must be before end date");
        }
        if (request.getLatitudeShift() == null || request.getLongitudeShift() == null) {
            throw new GeoPulseException(INVALID_DEBUG_EXPORT_REQUEST, "Latitude and longitude shift are required");
        }
    }

    private ExportJobResponse toResponse(ExportJob job) {
        ExportJobResponse response = new ExportJobResponse();
        response.setExportJobId(job.getJobId());
        response.setStatus(job.getStatus().name().toLowerCase());
        response.setProgress(job.getProgress());
        if (job.getProgressMessage() != null) {
            response.setProgressMessage(new MessageDescriptor(
                    "export.progress." + job.getStatus().name().toLowerCase(Locale.ROOT),
                    Map.of("progress", job.getProgress()),
                    job.getProgressMessage()));
        }
        response.setCreatedAt(job.getCreatedAt());
        response.setCompletedAt(job.getCompletedAt());
        response.setDataTypes(job.getDataTypes());
        response.setDateRange(job.getDateRange());
        response.setFileSizeBytes(job.getFileSizeBytes());
        if (job.getError() != null) {
            response.setError(new MessageDescriptor("export.error.failed", Map.of(), job.getError()));
        }
        if ("COMPLETED".equals(job.getStatus().name())) {
            response.setDownloadUrl("/api/v1/exports/" + job.getJobId() + "/content");
            response.setExpiresAt(job.getCreatedAt().plus(
                    settingsService.getInteger("export.job-expiry-hours"), ChronoUnit.HOURS));
        }
        return response;
    }

    private Response download(byte[] data, String contentType, String filename) {
        return Response.ok(data)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("Content-Type", contentType)
                .header("Content-Length", data.length)
                .build();
    }

    private String generateFilename(ExportJob job, UUID userId) {
        String extension = job.getFileExtension() == null ? "dat" : job.getFileExtension().replaceFirst("^\\.", "");
        String filename = switch (job.getFormat()) {
            case "owntracks" -> "owntracks-export-%s-%d.%s";
            case "gpx" -> "geopulse-gpx-export-%s-%d.%s";
            default -> "geopulse-export-%s-%d.%s";
        };
        return filename.formatted(userId.toString().substring(0, 8), job.getCreatedAt().getEpochSecond(), extension);
    }
}
