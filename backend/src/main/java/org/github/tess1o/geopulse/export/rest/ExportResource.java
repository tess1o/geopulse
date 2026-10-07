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
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.export.model.CreateExportRequest;
import org.github.tess1o.geopulse.export.model.DebugExportRequest;
import org.github.tess1o.geopulse.export.model.ExportDateRange;
import org.github.tess1o.geopulse.export.model.ExportFormat;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.export.model.ExportJobResponse;
import org.github.tess1o.geopulse.export.model.GpxLayout;
import org.github.tess1o.geopulse.export.model.OwnTracksLayout;
import org.github.tess1o.geopulse.export.service.DebugExportService;
import org.github.tess1o.geopulse.export.service.ExportJobManager;
import org.github.tess1o.geopulse.shared.api.SliceResponse;
import org.github.tess1o.geopulse.shared.api.MessageDescriptor;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.jboss.resteasy.reactive.RestResponse;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@Path("/exports")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = ApiTags.EXPORT)
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
    @Operation(summary = "Download a trip as GPX",
            description = "Returns the GPS track of a single timeline trip as a GPX file.")
    public Response exportSingleTrip(
            @Parameter(description = "Timeline trip ID, as returned by the timeline endpoints.")
            @PathParam("tripId") Long tripId) throws Exception {
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
    @Operation(summary = "Download a stay as GPX",
            description = "Returns the GPS points of a single timeline stay as a GPX file.")
    public Response exportSingleStay(
            @Parameter(description = "Timeline stay ID, as returned by the timeline endpoints.")
            @PathParam("stayId") Long stayId) throws Exception {
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
    @Operation(summary = "Start an export",
            description = "Starts a background export of a time range. Formats: `geopulse` (ZIP that can be imported "
                    + "back into GeoPulse; list what to include in `dataTypes`, such as `rawgps`, `timeline`, "
                    + "`favorites`, or `timelinelabels`; without `dataTypes` only raw GPS points are exported), and "
                    + "`gpx`, `owntracks`, `geojson`, and `csv` (raw GPS points). Poll `GET "
                    + "/api/v1/exports/{exportJobId}` until the status is `completed`, then download the file from "
                    + "`downloadUrl`. The number of export jobs per user is limited.")
    public ExportJobResponse createExport(CreateExportRequest request) {
        if (request == null) {
            throw new GeoPulseException(INVALID_EXPORT_REQUEST, "Request body is required");
        }
        validateDateRange(request.getStartTime(), request.getEndTime());
        ExportFormat format = request.getFormat() == null ? ExportFormat.GEOPULSE : request.getFormat();
        List<String> dataTypes = resolveDataTypes(format, request.getDataTypes());

        try {
            UUID userId = currentUserService.getCurrentUserId();
            return toResponse(exportJobManager.createExportJob(userId, dataTypes,
                    new ExportDateRange(request.getStartTime(), request.getEndTime()), format,
                    request.getGpxLayout() == null ? GpxLayout.SINGLE : request.getGpxLayout(),
                    request.getOwntracksLayout() == null ? OwnTracksLayout.OCAT : request.getOwntracksLayout()));
        } catch (IllegalStateException exception) {
            throw new GeoPulseException(RATE_LIMIT_EXCEEDED, RATE_LIMIT_EXCEEDED.title(), exception);
        }
    }

    /**
     * Only the native format lets the caller choose data types; every other format exports raw GPS points.
     */
    private static List<String> resolveDataTypes(ExportFormat format, List<String> requested) {
        if (format != ExportFormat.GEOPULSE || requested == null || requested.isEmpty()) {
            return List.of(ExportImportConstants.DataTypes.RAW_GPS);
        }
        List<String> unknown = requested.stream()
                .filter(dataType -> !ExportImportConstants.DataTypes.ALL.contains(dataType))
                .toList();
        if (!unknown.isEmpty()) {
            throw new GeoPulseException(INVALID_EXPORT_REQUEST, "Unknown data types: " + String.join(", ", unknown),
                    Map.of("field", "dataTypes", "unknown", unknown));
        }
        return List.copyOf(requested);
    }

    @GET
    @Path("/{exportJobId}")
    @APIResponse(responseCode = "200", description = "Export job status")
    @APIResponse(responseCode = "404", description = "Export job not found")
    @Operation(summary = "Get an export job",
            description = "Returns the status and progress of an export job. When it is `completed`, the response "
                    + "includes `downloadUrl` and the time the file expires.")
    public ExportJobResponse getExportStatus(
            @Parameter(description = "Export job ID.")
            @PathParam("exportJobId") UUID exportJobId) {
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
    @Operation(summary = "Download the CSV import template",
            description = "Returns an example CSV file with the columns the CSV import accepts.")
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
    @Operation(summary = "Download an export",
            description = "Downloads the file of a completed export job. The content type depends on the export "
                    + "format. Returns `409` while the job is still running and `410` after the file has expired.")
    public Response downloadExport(
            @Parameter(description = "Export job ID.")
            @PathParam("exportJobId") UUID exportJobId) {
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
    @Operation(summary = "List export jobs",
            description = "Returns the export jobs of the signed-in user, newest first, one page at a time.")
    public SliceResponse<ExportJobResponse> listExportJobs(
            @Parameter(description = "Page number, starting at 0.")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size, from 1 to 50. Defaults to 10.")
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
    @Operation(summary = "Delete an export job",
            description = "Deletes an export job and its file.")
    public RestResponse<Void> deleteExportJob(
            @Parameter(description = "Export job ID.")
            @PathParam("exportJobId") UUID exportJobId) {
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
    @Operation(summary = "Create a debug export",
            description = "Returns a ZIP archive with GPS data, and optionally the timeline and timeline settings, "
                    + "for a time range, with all coordinates shifted by the given latitude and longitude offsets "
                    + "to hide real locations. Share it when reporting a timeline issue; it can be loaded with "
                    + "`POST /api/v1/debug-imports`.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public Response createDebugExport(DebugExportRequest request) throws Exception {
        validateDebugRequest(request);
        UUID userId = currentUserService.getCurrentUserId();
        byte[] data = debugExportService.generateDebugExport(userId, request);
        return download(data, "application/zip",
                "geopulse-debug-%s-%d.zip".formatted(userId, Instant.now().getEpochSecond()));
    }

    private void validateDateRange(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) {
            throw new GeoPulseException(INVALID_DATE_RANGE, "startTime and endTime are required");
        }
        if (startTime.isAfter(Instant.now())) {
            throw new GeoPulseException(INVALID_DATE_RANGE, "Start time cannot be in the future");
        }
        if (startTime.isAfter(endTime)) {
            throw new GeoPulseException(INVALID_DATE_RANGE, "Start time must be before end time");
        }
    }

    private void validateDebugRequest(DebugExportRequest request) {
        if (request == null || request.getStartTime() == null || request.getEndTime() == null) {
            throw new GeoPulseException(INVALID_DEBUG_EXPORT_REQUEST, "startTime and endTime are required");
        }
        if (request.getStartTime().isAfter(Instant.now())) {
            throw new GeoPulseException(INVALID_DEBUG_EXPORT_REQUEST, "Start time cannot be in the future");
        }
        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new GeoPulseException(INVALID_DEBUG_EXPORT_REQUEST, "Start time must be before end time");
        }
        if (request.getLatitudeShift() == null || request.getLongitudeShift() == null) {
            throw new GeoPulseException(INVALID_DEBUG_EXPORT_REQUEST, "Latitude and longitude shift are required");
        }
    }

    private ExportJobResponse toResponse(ExportJob job) {
        ExportJobResponse response = new ExportJobResponse();
        response.setExportJobId(job.getJobId());
        response.setFormat(job.getFormat());
        response.setStatus(job.getStatus().name().toLowerCase());
        response.setProgress(job.getProgress());
        response.setProgressMessage(job.getProgressMessage());
        response.setCreatedAt(job.getCreatedAt());
        response.setCompletedAt(job.getCompletedAt());
        response.setDataTypes(job.getDataTypes());
        response.setStartTime(job.getDateRange().getStartDate());
        response.setEndTime(job.getDateRange().getEndDate());
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
            case OWNTRACKS -> "owntracks-export-%s-%d.%s";
            case GPX -> "geopulse-gpx-export-%s-%d.%s";
            default -> "geopulse-export-%s-%d.%s";
        };
        return filename.formatted(userId.toString().substring(0, 8), job.getCreatedAt().getEpochSecond(), extension);
    }
}
