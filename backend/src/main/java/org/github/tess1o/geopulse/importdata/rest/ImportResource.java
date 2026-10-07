package org.github.tess1o.geopulse.importdata.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.importdata.model.ImportFormat;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportJobResponse;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.ImportJobService;
import org.github.tess1o.geopulse.importdata.service.ImportTempFileService;
import org.github.tess1o.geopulse.shared.api.SliceResponse;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

/**
 * REST resource for managing import jobs and uploading files to import.
 * Chunked uploads for large files are handled by ImportUploadResource.
 */
@Path("/imports")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = ApiTags.IMPORT)
public class ImportResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ImportJobService importJobService;

    @Inject
    ImportTempFileService tempFileService;

    @ConfigProperty(name = "geopulse.import.chunked.max-file-size-gb", defaultValue = "10")
    int maxFileSizeGB;

    // ==================== DIRECT UPLOAD ====================

    /**
     * Direct file upload for small files (under chunked threshold).
     * Files are either kept in memory (small) or stored in temp directory (medium).
     * Large files (>80MB) should use the chunked upload flow instead.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Import a file",
            description = "Uploads a file and starts an import job. Supported formats: `owntracks` (.json), `gpx` "
                    + "(.gpx, or .zip with several GPX files), `google-timeline` (.json), `geojson` (.json, "
                    + ".geojson), `csv` (.csv), and `geopulse` (.zip created by a GeoPulse export). The optional "
                    + "`options` part (JSON) can limit the import to a time range and replace existing data in that "
                    + "range. Only one import can run at a time. The import runs in the background; poll `GET "
                    + "/api/v1/imports/{importJobId}` for progress. A reverse proxy in front of GeoPulse may limit "
                    + "the upload size.")
    public ImportJobResponse uploadFile(
            @RestForm("file") FileUpload file,
            @RestForm("format") @Schema(required = true, enumeration = {
                    "owntracks", "gpx", "google-timeline", "geojson", "csv", "geopulse"}) String format,
            @RestForm("options") @PartType(MediaType.APPLICATION_JSON) ImportOptions options) {
        UUID userId = currentUserService.getCurrentUserId();

        ImportFormat importFormat = ImportFormat.fromString(format);
        if (importFormat == null) {
            throw new GeoPulseException(INVALID_IMPORT_FORMAT,
                    "Unknown import format: " + format + ". Supported formats: " + ImportFormat.getSupportedFormats());
        }

        log.info("Received {} import request for user: {}", importFormat.getValue(), userId);

            // Check for existing active jobs
        if (importJobService.hasActiveImportJob(userId)) {
            throw new GeoPulseException(IMPORT_ACTIVE_JOB_CONFLICT,
                    "An import job is already in progress. Please wait for it to complete.");
        }

            // Validate file
        if (file == null || file.size() == 0) {
            throw new GeoPulseException(INVALID_IMPORT_FILE, "No file provided");
        }

            // Validate file size
        long maxFileSizeBytes = (long) maxFileSizeGB * 1024L * 1024L * 1024L;
        if (file.size() > maxFileSizeBytes) {
            throw new GeoPulseException(IMPORT_FILE_TOO_LARGE, "File exceeds the configured import limit",
                    Map.of("fileSizeBytes", file.size(), "maxFileSizeBytes", maxFileSizeBytes));
        }

            // Get file name and resolve GPX format if needed
        String fileName = file.fileName() != null ? file.fileName() : importFormat.getDefaultFileName();
        importFormat = ImportFormat.resolveGpxFormat(fileName, importFormat);

            // Validate file extension
        if (!importFormat.isValidExtension(fileName)) {
            throw new GeoPulseException(INVALID_IMPORT_FILE_TYPE,
                    "Invalid file type for " + importFormat.getValue() + " import. " +
                            "Allowed extensions: " + importFormat.getAllowedExtensions(),
                    Map.of("format", importFormat.getValue()));
        }

        ImportOptions importOptions = options != null ? options : new ImportOptions();
        importOptions.validateTimeRange();
        importOptions.setImportFormat(importFormat.getValue());

            // Create import job
        ImportJob job;
        try {
            job = createImportJob(userId, file, fileName, importOptions);
        } catch (IOException e) {
            throw new GeoPulseException(IMPORT_FAILED, "Failed to create import job", e);
        }

        log.info("Created {} import job: jobId={}, fileName={}, size={} MB",
                importFormat.getValue(), job.getJobId(), fileName, file.size() / (1024 * 1024));

        return ImportJobResponse.from(job);
    }

    @GET
    @Operation(summary = "List import jobs",
            description = "Returns the import jobs of the signed-in user, newest first, one page at a time. Jobs "
                    + "are kept in memory and the list is cleared when the server restarts.")
    public SliceResponse<ImportJobResponse> getImportJobs(
            @Parameter(description = "Page number, starting at 0.")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size, from 1 to 100. Defaults to 10.")
            @QueryParam("size") @DefaultValue("10") int size) {
        if (size < 1 || size > 100) {
            throw new GeoPulseException(INVALID_LIMIT, "Limit must be between 1 and 100", Map.of("min", 1, "max", 100));
        }
        if (page < 0) {
            throw new GeoPulseException(INVALID_PAGE, "Page must not be negative", Map.of("min", 0));
        }

        UUID userId = currentUserService.getCurrentUserId();
        List<ImportJob> jobs = importJobService.getUserImportJobs(userId, size + 1, page * size);
        boolean hasNext = jobs.size() > size;
        List<ImportJobResponse> items = jobs.stream().limit(size).map(ImportJobResponse::from).toList();
        return new SliceResponse<>(items, page, size, hasNext);
    }

    @GET
    @Path("/{importJobId}")
    @Operation(summary = "Get an import job",
            description = "Returns the status, progress, and result of an import job.")
    public ImportJobResponse getImportStatus(
            @Parameter(description = "Import job ID.")
            @PathParam("importJobId") UUID importJobId) {
        ImportJob job = importJobService.getImportJob(importJobId, currentUserService.getCurrentUserId());
        if (job == null) {
            throw new GeoPulseException(IMPORT_JOB_NOT_FOUND, "Import job not found",
                    Map.of("importJobId", importJobId.toString()));
        }
        return ImportJobResponse.from(job);
    }

    @DELETE
    @Path("/{importJobId}")
    @Operation(summary = "Delete an import job",
            description = "Removes an import job from the job list. Data that was already imported is kept.")
    public void deleteImportJob(
            @Parameter(description = "Import job ID.")
            @PathParam("importJobId") UUID importJobId) {
        if (!importJobService.deleteImportJob(importJobId, currentUserService.getCurrentUserId())) {
            throw new GeoPulseException(IMPORT_JOB_NOT_FOUND, "Import job not found",
                    Map.of("importJobId", importJobId.toString()));
        }
    }

    // ==================== HELPER METHODS ====================

    private ImportJob createImportJob(UUID userId, FileUpload file, String fileName, ImportOptions importOptions) throws IOException {
        ImportJob job;
        long fileSize = file.size();

        if (tempFileService.shouldUseTempFile(fileSize)) {
            // Large file: move to temp storage (no memory overhead)
            log.info("Large file detected ({} MB), using temp file storage", fileSize / (1024 * 1024));

            String tempFilePath = tempFileService.moveUploadedFileToTemp(
                    file.uploadedFile(), UUID.randomUUID(), fileName);

            // Create job with temp file path (no data in memory!)
            job = new ImportJob(userId, importOptions, fileName, new byte[0]);
            job.setTempFilePath(tempFilePath);
            job.setFileSizeBytes(fileSize);

            importJobService.registerJob(job);
        } else {
            // Small file: keep in memory (fast path)
            log.info("Small file detected ({} MB), keeping in memory", fileSize / (1024 * 1024));

            byte[] fileContent = java.nio.file.Files.readAllBytes(file.uploadedFile());
            job = new ImportJob(userId, importOptions, fileName, fileContent);
            job.setFileSizeBytes(fileSize);

            importJobService.registerJob(job);
        }

        return job;
    }
}
