package org.github.tess1o.geopulse.importdata.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.importdata.model.*;
import org.github.tess1o.geopulse.importdata.service.ChunkedUploadService;
import org.github.tess1o.geopulse.importdata.service.ImportJobService;
import org.github.tess1o.geopulse.importdata.service.ImportTempFileService;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

/**
 * Unified REST resource for handling all file imports.
 * Supports both direct uploads (small files) and chunked uploads (large files).
 * All import formats are handled through a single endpoint with a format parameter.
 */
@Path("/api/import/upload")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = "User: Import and Export", description = "Upload files and manage chunked imports.")
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
    public ImportJobResponse uploadFile(
            @RestForm("file") FileUpload file,
            @RestForm("format") String format,
            @RestForm("options") @PartType(MediaType.TEXT_PLAIN) String options) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            // Validate format
            ImportFormat importFormat = ImportFormat.fromString(format);
            if (importFormat == null) {
                throw problem(INVALID_IMPORT_FORMAT,
                        "Unknown import format: " + format + ". Supported formats: " + ImportFormat.getSupportedFormats());
            }

            log.info("Received {} import request for user: {}", importFormat.getValue(), userId);

            // Check for existing active jobs
            if (importJobService.hasActiveImportJob(userId)) {
                throw problem(IMPORT_ACTIVE_JOB_CONFLICT,
                        "An import job is already in progress. Please wait for it to complete.");
            }

            // Validate file
            if (file == null || file.size() == 0) {
                throw problem(INVALID_IMPORT_FILE, "No file provided");
            }

            // Validate file size
            long maxFileSizeBytes = (long) maxFileSizeGB * 1024L * 1024L * 1024L;
            if (file.size() > maxFileSizeBytes) {
                throw problem(IMPORT_FILE_TOO_LARGE, "File exceeds the configured import limit",
                        Map.of("fileSizeBytes", file.size(), "maxFileSizeBytes", maxFileSizeBytes));
            }

            // Get file name and resolve GPX format if needed
            String fileName = file.fileName() != null ? file.fileName() : importFormat.getDefaultFileName();
            importFormat = ImportFormat.resolveGpxFormat(fileName, importFormat);

            // Validate file extension
            if (!importFormat.isValidExtension(fileName)) {
                throw problem(INVALID_IMPORT_FILE_TYPE,
                        "Invalid file type for " + importFormat.getValue() + " import. " +
                                "Allowed extensions: " + importFormat.getAllowedExtensions(),
                        Map.of("format", importFormat.getValue()));
            }

            // Parse options
            ImportOptions importOptions;
            try {
                importOptions = objectMapper.readValue(options, ImportOptions.class);
                importOptions.setImportFormat(importFormat.getValue());
            } catch (Exception e) {
                log.error("Failed to parse import options", e);
                throw problem(INVALID_IMPORT_OPTIONS, "Invalid import options format");
            }

            // Create import job
            ImportJob job = createImportJob(userId, file, fileName, importOptions);

            log.info("Created {} import job: jobId={}, fileName={}, size={} MB",
                    importFormat.getValue(), job.getJobId(), fileName, file.size() / (1024 * 1024));

            return ImportJobResponse.from(job);

        } catch (IllegalStateException e) {
            throw problem(IMPORT_RATE_LIMITED, e.getMessage());
        } catch (io.quarkiverse.httpproblem.HttpProblem e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create import job", e);
            throw problem(IMPORT_FAILED, "Failed to create import job");
        }
    }

    // ==================== CHUNKED UPLOAD ====================

    /**
     * Initialize a chunked upload session for large files.
     * Frontend splits files >80MB into chunks to bypass upload limits (e.g., Cloudflare 100MB).
     */
    @POST
    @Path("/init")
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

        } catch (io.quarkiverse.httpproblem.HttpProblem e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to initialize chunked upload", e);
            throw problem(IMPORT_FAILED, "Failed to initialize chunked upload");
        }
    }

    /**
     * Upload a single chunk of a chunked upload.
     */
    @POST
    @Path("/{uploadId}/chunk")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ChunkUploadResponse uploadChunk(
            @PathParam("uploadId") UUID uploadId,
            @RestForm("chunkIndex") int chunkIndex,
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
        } catch (io.quarkiverse.httpproblem.HttpProblem e) {
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
    @Path("/{uploadId}/complete")
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
        } catch (io.quarkiverse.httpproblem.HttpProblem e) {
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
    @Path("/{uploadId}/status")
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

        } catch (io.quarkiverse.httpproblem.HttpProblem e) {
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
    @Path("/{uploadId}")
    public void abortUpload(@PathParam("uploadId") UUID uploadId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            boolean deleted = chunkedUploadService.abortUpload(uploadId, userId);
            if (!deleted) {
                throw problem(IMPORT_UPLOAD_NOT_FOUND, "Upload session not found");
            }

        } catch (io.quarkiverse.httpproblem.HttpProblem e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to abort upload", e);
            throw problem(IMPORT_FAILED, "Failed to abort upload");
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

    private ChunkUploadResponse createChunkResponse(ChunkedUploadSession session, int chunkIndex) {
        return new ChunkUploadResponse(
                chunkIndex,
                session.getReceivedChunkCount(),
                session.getTotalChunks(),
                session.getProgressPercentage(),
                session.isComplete());
    }
}
