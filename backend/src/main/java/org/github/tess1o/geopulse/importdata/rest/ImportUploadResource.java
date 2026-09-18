package org.github.tess1o.geopulse.importdata.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.httpproblem.HttpProblem;
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

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

/**
 * REST resource for chunked uploads of large import files.
 * Direct uploads of small files are handled by ImportResource.
 */
@Path("")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = "User: Import and Export", description = "Upload large files in chunks and manage chunked imports.")
public class ImportUploadResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ImportJobService importJobService;

    @Inject
    ChunkedUploadService chunkedUploadService;

    @Inject
    ImportTempFileService tempFileService;

    @Inject
    ObjectMapper objectMapper;

    // ==================== CHUNKED UPLOAD ====================

    /**
     * Initialize a chunked upload session for large files.
     * Frontend splits files >80MB into chunks to bypass upload limits (e.g., Cloudflare 100MB).
     */
    @POST
    @Path("/import-uploads")
    @Consumes(MediaType.APPLICATION_JSON)
    public ChunkedUploadInitResponse initializeChunkedUpload(ChunkedUploadInitRequest request) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            if (request == null) {
                throw problem(INVALID_IMPORT_REQUEST, "Upload request is required");
            }

            // Validate format
            ImportFormat importFormat = ImportFormat.fromString(request.getImportFormat());
            if (importFormat == null) {
                throw problem(INVALID_IMPORT_FORMAT,
                        "Unknown import format: " + request.getImportFormat() +
                                ". Supported formats: " + ImportFormat.getSupportedFormats());
            }

            log.info("Initializing chunked upload for user {}: fileName={}, fileSize={} MB, format={}",
                    userId, request.getFileName(), request.getFileSize() / (1024 * 1024),
                    importFormat.getValue());

            // Check for existing active import job
            if (importJobService.hasActiveImportJob(userId)) {
                throw problem(IMPORT_ACTIVE_JOB_CONFLICT,
                        "An import job is already in progress. Please wait for it to complete.");
            }

            // Check for existing active chunked upload
            if (chunkedUploadService.hasActiveUpload(userId)) {
                throw problem(IMPORT_ACTIVE_UPLOAD_CONFLICT,
                        "A chunked upload is already in progress. Please complete or abort it first.");
            }

            // Validate request
            if (request.getFileName() == null || request.getFileName().isBlank()) {
                throw problem(INVALID_IMPORT_REQUEST, "File name is required");
            }

            if (request.getFileSize() <= 0) {
                throw problem(INVALID_IMPORT_REQUEST, "File size must be positive",
                        Map.of("min", 1));
            }

            // Validate file size against maximum allowed
            long maxFileSizeBytes = chunkedUploadService.getMaxFileSizeBytes();
            if (request.getFileSize() > maxFileSizeBytes) {
                throw problem(IMPORT_FILE_TOO_LARGE, "File exceeds the configured import limit",
                        Map.of("fileSizeBytes", request.getFileSize(), "maxFileSizeBytes", maxFileSizeBytes));
            }

            // Note: totalChunks from frontend is ignored - backend calculates it based on configured chunk size

            // Resolve GPX format based on file extension
            String resolvedFormat = ImportFormat.resolveGpxFormat(
                    request.getFileName(), importFormat).getValue();

            // Validate file extension
            ImportFormat resolvedImportFormat = ImportFormat.fromString(resolvedFormat);
            if (!resolvedImportFormat.isValidExtension(request.getFileName())) {
                throw problem(INVALID_IMPORT_FILE_TYPE,
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

        } catch (HttpProblem e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to initialize chunked upload", e);
            throw problem(IMPORT_FAILED, "Failed to initialize chunked upload");
        }
    }

    /**
     * Upload a single chunk of a chunked upload.
     */
    @PUT
    @Path("/import-uploads/{uploadId}/parts/{chunkIndex}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ChunkUploadResponse uploadChunk(
            @PathParam("uploadId") UUID uploadId,
            @PathParam("chunkIndex") int chunkIndex,
            @RestForm("chunk") FileUpload chunkFile) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            // Validate session exists and belongs to user
            Optional<ChunkedUploadSession> sessionOpt = chunkedUploadService.getUploadStatus(uploadId, userId);
            if (sessionOpt.isEmpty()) {
                throw problem(IMPORT_UPLOAD_NOT_FOUND, "Upload session not found");
            }

            ChunkedUploadSession session = sessionOpt.get();

            // Check if session has expired
            if (session.isExpired()) {
                throw problem(IMPORT_UPLOAD_EXPIRED, "Upload session has expired");
            }

            // Check session status
            if (session.getStatus() != UploadStatus.UPLOADING) {
                throw problem(IMPORT_UPLOAD_INVALID_STATE, "Upload is not accepting chunks",
                        Map.of("status", session.getStatus().value()));
            }

            // Validate chunk file
            if (chunkFile == null || chunkFile.size() == 0) {
                throw problem(INVALID_IMPORT_CHUNK, "No chunk data provided");
            }

            // Validate chunk index
            if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
                throw problem(INVALID_IMPORT_CHUNK_INDEX, "Invalid chunk index",
                        Map.of("chunkIndex", chunkIndex, "min", 0, "max", session.getTotalChunks() - 1));
            }

            // Check if chunk already received (idempotent)
            if (session.hasChunk(chunkIndex)) {
                log.info("Chunk {} already received for upload {}, skipping", chunkIndex, uploadId);
                return createChunkResponse(session, chunkIndex);
            }

            // Save chunk to disk
            try (InputStream chunkStream = java.nio.file.Files.newInputStream(chunkFile.uploadedFile())) {
                chunkedUploadService.saveChunk(uploadId, chunkIndex, chunkStream);
            }

            log.debug("Received chunk {} for upload {}, progress: {}/{}",
                    chunkIndex, uploadId, session.getReceivedChunkCount(), session.getTotalChunks());

            return createChunkResponse(session, chunkIndex);

        } catch (IllegalStateException e) {
            log.warn("Invalid state for chunk upload: {}", e.getMessage());
            throw problem(IMPORT_UPLOAD_INVALID_STATE, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument for chunk upload: {}", e.getMessage());
            throw problem(INVALID_IMPORT_REQUEST, e.getMessage());
        } catch (IOException e) {
            log.error("Failed to save chunk", e);
            throw problem(IMPORT_CHUNK_SAVE_FAILED, "Failed to save chunk");
        } catch (HttpProblem e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to process chunk upload", e);
            throw problem(IMPORT_FAILED, "Failed to process chunk");
        }
    }

    /**
     * Complete a chunked upload and create an import job.
     */
    @POST
    @Path("/import-uploads/{uploadId}/completion")
    public ImportJobResponse completeChunkedUpload(@PathParam("uploadId") UUID uploadId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            // Validate session exists and belongs to user
            Optional<ChunkedUploadSession> sessionOpt = chunkedUploadService.getUploadStatus(uploadId, userId);
            if (sessionOpt.isEmpty()) {
                throw problem(IMPORT_UPLOAD_NOT_FOUND, "Upload session not found");
            }

            ChunkedUploadSession session = sessionOpt.get();

            // Check if all chunks received
            if (!session.isComplete()) {
                throw problem(IMPORT_UPLOAD_INCOMPLETE, "Upload is not complete",
                        Map.of("receivedChunks", session.getReceivedChunkCount(),
                                "totalChunks", session.getTotalChunks()));
            }

            // Check for existing active import job (in case one was created while uploading)
            if (importJobService.hasActiveImportJob(userId)) {
                throw problem(IMPORT_ACTIVE_JOB_CONFLICT,
                        "An import job is already in progress. Please wait for it to complete.");
            }

            log.info("Completing chunked upload: uploadId={}, fileName={}, format={}",
                    uploadId, session.getFileName(), session.getImportFormat());

            // Assemble chunks into final file
            java.nio.file.Path assembledFile = chunkedUploadService.assembleFile(uploadId);

            // Parse import options
            ImportOptions importOptions;
            try {
                if (session.getOptions() != null && !session.getOptions().isBlank()) {
                    importOptions = objectMapper.readValue(session.getOptions(), ImportOptions.class);
                } else {
                    importOptions = new ImportOptions();
                }
                importOptions.setImportFormat(session.getImportFormat());
            } catch (Exception e) {
                log.error("Failed to parse import options", e);
                importOptions = new ImportOptions();
                importOptions.setImportFormat(session.getImportFormat());
            }

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
            log.error("Failed to assemble chunked upload", e);
            throw problem(IMPORT_ASSEMBLY_FAILED, "Failed to assemble uploaded chunks");
        } catch (HttpProblem e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to complete chunked upload", e);
            throw problem(IMPORT_FAILED, "Failed to complete chunked upload");
        }
    }

    /**
     * Get the status of a chunked upload session.
     */
    @GET
    @Path("/import-uploads/{uploadId}")
    public ChunkedUploadStatusResponse getUploadStatus(@PathParam("uploadId") UUID uploadId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            Optional<ChunkedUploadSession> sessionOpt = chunkedUploadService.getUploadStatus(uploadId, userId);
            if (sessionOpt.isEmpty()) {
                throw problem(IMPORT_UPLOAD_NOT_FOUND, "Upload session not found");
            }

            ChunkedUploadSession session = sessionOpt.get();

            return new ChunkedUploadStatusResponse(
                    session.getUploadId(), session.getFileName(), session.getFileSize(),
                    session.getTotalChunks(), session.getReceivedChunkCount(), session.getProgressPercentage(),
                    session.getStatus(), session.isComplete(), session.isExpired(), session.getExpiresAt(),
                    Set.copyOf(session.getReceivedChunks()));

        } catch (HttpProblem e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get upload status", e);
            throw problem(IMPORT_FAILED, "Failed to get upload status");
        }
    }

    /**
     * Abort a chunked upload and cleanup temp files.
     */
    @DELETE
    @Path("/import-uploads/{uploadId}")
    public void abortUpload(@PathParam("uploadId") UUID uploadId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            boolean deleted = chunkedUploadService.abortUpload(uploadId, userId);
            if (!deleted) {
                throw problem(IMPORT_UPLOAD_NOT_FOUND, "Upload session not found");
            }

        } catch (HttpProblem e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to abort upload", e);
            throw problem(IMPORT_FAILED, "Failed to abort upload");
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
