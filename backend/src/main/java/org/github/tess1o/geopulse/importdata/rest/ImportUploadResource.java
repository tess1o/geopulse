package org.github.tess1o.geopulse.importdata.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.importdata.model.*;
import org.github.tess1o.geopulse.importdata.service.ChunkedUploadService;
import org.github.tess1o.geopulse.importdata.service.ImportJobService;
import org.github.tess1o.geopulse.importdata.service.ImportTempFileService;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

/**
 * REST resource for chunked uploads of large import files.
 * Direct uploads of small files are handled by ImportResource.
 */
@Path("")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = ApiTags.IMPORT)
public class ImportUploadResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ImportJobService importJobService;

    @Inject
    ChunkedUploadService chunkedUploadService;

    @Inject
    ImportTempFileService tempFileService;

    // ==================== CHUNKED UPLOAD ====================

    /**
     * Initialize a chunked upload session for large files.
     * Frontend splits files >80MB into chunks to bypass upload limits (e.g., Cloudflare 100MB).
     */
    @POST
    @Path("/import-uploads")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Start a chunked upload",
            description = "Starts uploading a large import file in parts. Send the file name, total size, import "
                    + "format, and optional import options. The response contains the upload ID, the chunk size the "
                    + "server expects, the number of chunks, and when the upload expires. Then upload every chunk "
                    + "and call the completion endpoint. Only one chunked upload and one import job can be active "
                    + "at a time.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public ChunkedUploadInitResponse initializeChunkedUpload(ChunkedUploadInitRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
            if (request == null) {
                throw new GeoPulseException(INVALID_IMPORT_REQUEST, "Upload request is required");
            }

            // Validate format
            ImportFormat importFormat = ImportFormat.fromString(request.getImportFormat());
            if (importFormat == null) {
                throw new GeoPulseException(INVALID_IMPORT_FORMAT,
                        "Unknown import format: " + request.getImportFormat() +
                                ". Supported formats: " + ImportFormat.getSupportedFormats());
            }
            if (request.getOptions() != null) {
                request.getOptions().validateTimeRange();
            }

            log.info("Initializing chunked upload for user {}: fileName={}, fileSize={} MB, format={}",
                    userId, request.getFileName(), request.getFileSize() / (1024 * 1024),
                    importFormat.getValue());

            // Check for existing active import job
            if (importJobService.hasActiveImportJob(userId)) {
                throw new GeoPulseException(IMPORT_ACTIVE_JOB_CONFLICT,
                        "An import job is already in progress. Please wait for it to complete.");
            }

            // Check for existing active chunked upload
            if (chunkedUploadService.hasActiveUpload(userId)) {
                throw new GeoPulseException(IMPORT_ACTIVE_UPLOAD_CONFLICT,
                        "A chunked upload is already in progress. Please complete or abort it first.");
            }

            // Validate request
            if (request.getFileName() == null || request.getFileName().isBlank()) {
                throw new GeoPulseException(INVALID_IMPORT_REQUEST, "File name is required");
            }

            if (request.getFileSize() <= 0) {
                throw new GeoPulseException(INVALID_IMPORT_REQUEST, "File size must be positive",
                        Map.of("min", 1));
            }

            // Validate file size against maximum allowed
            long maxFileSizeBytes = chunkedUploadService.getMaxFileSizeBytes();
            if (request.getFileSize() > maxFileSizeBytes) {
                throw new GeoPulseException(IMPORT_FILE_TOO_LARGE, "File exceeds the configured import limit",
                        Map.of("fileSizeBytes", request.getFileSize(), "maxFileSizeBytes", maxFileSizeBytes));
            }

            // Note: totalChunks from frontend is ignored - backend calculates it based on configured chunk size

            // Resolve GPX format based on file extension
            String resolvedFormat = ImportFormat.resolveGpxFormat(
                    request.getFileName(), importFormat).getValue();

            // Validate file extension
            ImportFormat resolvedImportFormat = ImportFormat.fromString(resolvedFormat);
            if (!resolvedImportFormat.isValidExtension(request.getFileName())) {
                throw new GeoPulseException(INVALID_IMPORT_FILE_TYPE,
                        "Invalid file type for " + resolvedFormat + " import. " +
                                "Allowed extensions: " + resolvedImportFormat.getAllowedExtensions(),
                        Map.of("format", resolvedFormat));
            }

            // Create session - totalChunks is calculated by the service based on configured chunk size
            ChunkedUploadSession session = chunkedUploadService.initializeUpload(
                    userId,
                    request.getFileName(),
                    request.getFileSize(),
                    resolvedFormat,
                    request.getOptions()
            );

        return new ChunkedUploadInitResponse(
                session.getUploadId(), session.getTotalChunks(),
                chunkedUploadService.getChunkSizeBytes(), session.getExpiresAt());
    }

    /**
     * Upload a single chunk of a chunked upload.
     */
    @PUT
    @Path("/import-uploads/{uploadId}/parts/{chunkIndex}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Upload a chunk",
            description = "Uploads one part of a chunked upload as the multipart field `chunk`. Chunks can arrive in "
                    + "any order; sending a chunk again is ignored.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public ChunkUploadResponse uploadChunk(
            @Parameter(description = "Upload ID returned when the upload was started.")
            @PathParam("uploadId") UUID uploadId,
            @Parameter(description = "Zero-based chunk index.")
            @PathParam("chunkIndex") int chunkIndex,
            @RestForm("chunk") FileUpload chunkFile) {
        UUID userId = currentUserService.getCurrentUserId();

            // Validate session exists and belongs to user
            Optional<ChunkedUploadSession> sessionOpt = chunkedUploadService.getUploadStatus(uploadId, userId);
            if (sessionOpt.isEmpty()) {
                throw new GeoPulseException(IMPORT_UPLOAD_NOT_FOUND, "Upload session not found");
            }

            ChunkedUploadSession session = sessionOpt.get();

            // Check if session has expired
            if (session.isExpired()) {
                throw new GeoPulseException(IMPORT_UPLOAD_EXPIRED, "Upload session has expired");
            }

            // Check session status
            if (session.getStatus() != UploadStatus.UPLOADING) {
                throw new GeoPulseException(IMPORT_UPLOAD_INVALID_STATE, "Upload is not accepting chunks",
                        Map.of("status", session.getStatus().value()));
            }

            // Validate chunk file
            if (chunkFile == null || chunkFile.size() == 0) {
                throw new GeoPulseException(INVALID_IMPORT_CHUNK, "No chunk data provided");
            }

            // Validate chunk index
            if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
                throw new GeoPulseException(INVALID_IMPORT_CHUNK_INDEX, "Invalid chunk index",
                        Map.of("chunkIndex", chunkIndex, "min", 0, "max", session.getTotalChunks() - 1));
            }

            // Check if chunk already received (idempotent)
            if (session.hasChunk(chunkIndex)) {
                log.info("Chunk {} already received for upload {}, skipping", chunkIndex, uploadId);
                return createChunkResponse(session, chunkIndex);
            }

        try (InputStream chunkStream = java.nio.file.Files.newInputStream(chunkFile.uploadedFile())) {
            try {
                chunkedUploadService.saveChunk(uploadId, chunkIndex, chunkStream);
            } catch (IllegalStateException e) {
                throw new GeoPulseException(IMPORT_UPLOAD_INVALID_STATE, IMPORT_UPLOAD_INVALID_STATE.title(), e);
            } catch (IllegalArgumentException e) {
                throw new GeoPulseException(INVALID_IMPORT_REQUEST, INVALID_IMPORT_REQUEST.title(), e);
            }
        } catch (IOException e) {
            throw new GeoPulseException(IMPORT_CHUNK_SAVE_FAILED, "Failed to save chunk", e);
        }

        log.debug("Received chunk {} for upload {}, progress: {}/{}",
                chunkIndex, uploadId, session.getReceivedChunkCount(), session.getTotalChunks());
        return createChunkResponse(session, chunkIndex);
    }

    /**
     * Complete a chunked upload and create an import job.
     */
    @POST
    @Path("/import-uploads/{uploadId}/completion")
    @Operation(summary = "Finish a chunked upload",
            description = "Assembles the uploaded chunks and starts the import job. Fails if any chunk is missing. "
                    + "Returns the import job; poll `GET /api/v1/imports/{importJobId}` for progress.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public ImportJobResponse completeChunkedUpload(
            @Parameter(description = "Upload ID.")
            @PathParam("uploadId") UUID uploadId) {
        UUID userId = currentUserService.getCurrentUserId();

            // Validate session exists and belongs to user
            Optional<ChunkedUploadSession> sessionOpt = chunkedUploadService.getUploadStatus(uploadId, userId);
            if (sessionOpt.isEmpty()) {
                throw new GeoPulseException(IMPORT_UPLOAD_NOT_FOUND, "Upload session not found");
            }

            ChunkedUploadSession session = sessionOpt.get();

            // Check if all chunks received
            if (!session.isComplete()) {
                throw new GeoPulseException(IMPORT_UPLOAD_INCOMPLETE, "Upload is not complete",
                        Map.of("receivedChunks", session.getReceivedChunkCount(),
                                "totalChunks", session.getTotalChunks()));
            }

            // Check for existing active import job (in case one was created while uploading)
            if (importJobService.hasActiveImportJob(userId)) {
                throw new GeoPulseException(IMPORT_ACTIVE_JOB_CONFLICT,
                        "An import job is already in progress. Please wait for it to complete.");
            }

            log.info("Completing chunked upload: uploadId={}, fileName={}, format={}",
                    uploadId, session.getFileName(), session.getImportFormat());

        try {
            java.nio.file.Path assembledFile = chunkedUploadService.assembleFile(uploadId);

            ImportOptions importOptions = session.getOptions() != null ? session.getOptions() : new ImportOptions();
            importOptions.setImportFormat(session.getImportFormat());

            // Move assembled file to import temp directory
            String tempFilePath = tempFileService.moveUploadedFileToTemp(
                    assembledFile, UUID.randomUUID(), session.getFileName());

            // Create import job with temp file path (no data in memory!)
            ImportJob job = new ImportJob(userId, importOptions, session.getFileName(), new byte[0]);
            job.setTempFilePath(tempFilePath);
            job.setFileSizeBytes(session.getFileSize());

            importJobService.registerJob(job);

            // Cleanup the session directory (assembled file was moved)
            chunkedUploadService.abortUpload(uploadId, userId);

            log.info("Created import job {} from chunked upload {}", job.getJobId(), uploadId);

            return ImportJobResponse.from(job);

        } catch (IOException e) {
            throw new GeoPulseException(IMPORT_ASSEMBLY_FAILED, "Failed to assemble uploaded chunks", e);
        }
    }

    /**
     * Get the status of a chunked upload session.
     */
    @GET
    @Path("/import-uploads/{uploadId}")
    @Operation(summary = "Get chunked upload status",
            description = "Returns which chunks have been received, the progress, and whether the upload is complete "
                    + "or expired. Use it to resume an interrupted upload.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public ChunkedUploadStatusResponse getUploadStatus(
            @Parameter(description = "Upload ID.")
            @PathParam("uploadId") UUID uploadId) {
        UUID userId = currentUserService.getCurrentUserId();

            Optional<ChunkedUploadSession> sessionOpt = chunkedUploadService.getUploadStatus(uploadId, userId);
            if (sessionOpt.isEmpty()) {
                throw new GeoPulseException(IMPORT_UPLOAD_NOT_FOUND, "Upload session not found");
            }

            ChunkedUploadSession session = sessionOpt.get();

        return new ChunkedUploadStatusResponse(
                session.getUploadId(), session.getFileName(), session.getFileSize(),
                session.getTotalChunks(), session.getReceivedChunkCount(), session.getProgressPercentage(),
                session.getStatus(), session.isComplete(), session.isExpired(), session.getExpiresAt(),
                Set.copyOf(session.getReceivedChunks()));
    }

    /**
     * Abort a chunked upload and cleanup temp files.
     */
    @DELETE
    @Path("/import-uploads/{uploadId}")
    @Operation(summary = "Cancel a chunked upload",
            description = "Cancels a chunked upload and deletes the chunks received so far.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public void abortUpload(
            @Parameter(description = "Upload ID.")
            @PathParam("uploadId") UUID uploadId) {
        UUID userId = currentUserService.getCurrentUserId();
        boolean deleted = chunkedUploadService.abortUpload(uploadId, userId);
        if (!deleted) {
            throw new GeoPulseException(IMPORT_UPLOAD_NOT_FOUND, "Upload session not found");
        }
    }

    // ==================== HELPER METHODS ====================

    private ChunkUploadResponse createChunkResponse(ChunkedUploadSession session, int chunkIndex) {
        return new ChunkUploadResponse(
                chunkIndex,
                session.getReceivedChunkCount(),
                session.getTotalChunks(),
                session.getProgressPercentage(),
                session.isComplete());
    }
}
