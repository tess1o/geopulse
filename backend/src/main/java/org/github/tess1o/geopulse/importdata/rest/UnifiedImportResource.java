package org.github.tess1o.geopulse.importdata.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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

/**
 * Unified REST resource for handling all file imports.
 * Supports both direct uploads (small files) and chunked uploads (large files).
 * All import formats are handled through a single endpoint with a format parameter.
 */
@Path("/api/import/upload")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class UnifiedImportResource {

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
    public Response uploadFile(
            @RestForm("file") FileUpload file,
            @RestForm("format") String format,
            @RestForm("options") @PartType(MediaType.TEXT_PLAIN) String options) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            // Validate format
            ImportFormat importFormat = ImportFormat.fromString(format);
            if (importFormat == null) {
                return badRequest("INVALID_FORMAT",
                        "Unknown import format: " + format + ". Supported formats: " + ImportFormat.getSupportedFormats());
            }

            log.info("Received {} import request for user: {}", importFormat.getValue(), userId);

            // Check for existing active jobs
            if (importJobService.hasActiveImportJob(userId)) {
                return conflict("ACTIVE_JOB_EXISTS",
                        "An import job is already in progress. Please wait for it to complete.");
            }

            // Validate file
            if (file == null || file.size() == 0) {
                return badRequest("INVALID_FILE", "No file provided");
            }

            // Validate file size
            long maxFileSizeBytes = (long) maxFileSizeGB * 1024L * 1024L * 1024L;
            if (file.size() > maxFileSizeBytes) {
                return badRequest("FILE_TOO_LARGE",
                        String.format("File size (%d MB) exceeds %d GB limit",
                                file.size() / (1024 * 1024), maxFileSizeGB));
            }

            // Get file name and resolve GPX format if needed
            String fileName = file.fileName() != null ? file.fileName() : importFormat.getDefaultFileName();
            importFormat = ImportFormat.resolveGpxFormat(fileName, importFormat);

            // Validate file extension
            if (!importFormat.isValidExtension(fileName)) {
                return badRequest("INVALID_FILE_TYPE",
                        "Invalid file type for " + importFormat.getValue() + " import. " +
                                "Allowed extensions: " + importFormat.getAllowedExtensions());
            }

            // Parse options
            ImportOptions importOptions;
            try {
                importOptions = objectMapper.readValue(options, ImportOptions.class);
                importOptions.setImportFormat(importFormat.getValue());
            } catch (Exception e) {
                log.error("Failed to parse import options", e);
                return badRequest("INVALID_OPTIONS", "Invalid import options format: " + e.getMessage());
            }

            // Create import job
            ImportJob job = createImportJob(userId, file, fileName, importOptions);

            log.info("Created {} import job: jobId={}, fileName={}, size={} MB",
                    importFormat.getValue(), job.getJobId(), fileName, file.size() / (1024 * 1024));

            return Response.ok(createJobResponse(job, importFormat.getValue() + " import job created successfully")).build();

        } catch (IllegalStateException e) {
            return Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .entity(createErrorResponse("RATE_LIMIT_EXCEEDED", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create import job", e);
            return serverError("INTERNAL_ERROR", "Failed to create import job");
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
    public Response initializeChunkedUpload(ChunkedUploadInitRequest request) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            // Validate format
            ImportFormat importFormat = ImportFormat.fromString(request.getImportFormat());
            if (importFormat == null) {
                return badRequest("INVALID_FORMAT",
                        "Unknown import format: " + request.getImportFormat() +
                                ". Supported formats: " + ImportFormat.getSupportedFormats());
            }

            log.info("Initializing chunked upload for user {}: fileName={}, fileSize={} MB, format={}",
                    userId, request.getFileName(), request.getFileSize() / (1024 * 1024),
                    importFormat.getValue());

            // Check for existing active import job
            if (importJobService.hasActiveImportJob(userId)) {
                return conflict("ACTIVE_JOB_EXISTS",
                        "An import job is already in progress. Please wait for it to complete.");
            }

            // Check for existing active chunked upload
            if (chunkedUploadService.hasActiveUpload(userId)) {
                return conflict("ACTIVE_UPLOAD_EXISTS",
                        "A chunked upload is already in progress. Please complete or abort it first.");
            }

            // Validate request
            if (request.getFileName() == null || request.getFileName().isBlank()) {
                return badRequest("INVALID_REQUEST", "File name is required");
            }

            if (request.getFileSize() <= 0) {
                return badRequest("INVALID_REQUEST", "File size must be positive");
            }

            // Validate file size against maximum allowed
            long maxFileSizeBytes = chunkedUploadService.getMaxFileSizeBytes();
            if (request.getFileSize() > maxFileSizeBytes) {
                long maxGB = maxFileSizeBytes / (1024L * 1024L * 1024L);
                return badRequest("FILE_TOO_LARGE",
                        "File size exceeds maximum allowed size of " + maxGB + " GB");
            }

            // Note: totalChunks from frontend is ignored - backend calculates it based on configured chunk size

            // Resolve GPX format based on file extension
            String resolvedFormat = ImportFormat.resolveGpxFormat(
                    request.getFileName(), importFormat).getValue();

            // Validate file extension
            ImportFormat resolvedImportFormat = ImportFormat.fromString(resolvedFormat);
            if (!resolvedImportFormat.isValidExtension(request.getFileName())) {
                return badRequest("INVALID_FILE_TYPE",
                        "Invalid file type for " + resolvedFormat + " import. " +
                                "Allowed extensions: " + resolvedImportFormat.getAllowedExtensions());
            }

            // Create session - totalChunks is calculated by the service based on configured chunk size
            ChunkedUploadSession session = chunkedUploadService.initializeUpload(
                    userId,
                    request.getFileName(),
                    request.getFileSize(),
                    resolvedFormat,
                    request.getOptions()
            );

            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("uploadId", session.getUploadId());
            response.put("totalChunks", session.getTotalChunks());
            response.put("chunkSizeBytes", chunkedUploadService.getChunkSizeBytes());
            response.put("expiresAt", session.getExpiresAt());
            response.put("message", "Chunked upload initialized successfully");

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Failed to initialize chunked upload", e);
            return serverError("INTERNAL_ERROR", "Failed to initialize chunked upload");
        }
    }

    /**
     * Upload a single chunk of a chunked upload.
     */
    @POST
    @Path("/{uploadId}/chunk")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadChunk(
            @PathParam("uploadId") UUID uploadId,
            @RestForm("chunkIndex") int chunkIndex,
            @RestForm("chunk") FileUpload chunkFile) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            // Validate session exists and belongs to user
            Optional<ChunkedUploadSession> sessionOpt = chunkedUploadService.getUploadStatus(uploadId, userId);
            if (sessionOpt.isEmpty()) {
                return notFound("UPLOAD_NOT_FOUND", "Upload session not found");
            }

            ChunkedUploadSession session = sessionOpt.get();

            // Check if session has expired
            if (session.isExpired()) {
                return gone("UPLOAD_EXPIRED", "Upload session has expired");
            }

            // Check session status
            if (session.getStatus() != UploadStatus.UPLOADING) {
                return conflict("INVALID_STATE",
                        "Upload is not in uploading state: " + session.getStatus());
            }

            // Validate chunk file
            if (chunkFile == null || chunkFile.size() == 0) {
                return badRequest("INVALID_CHUNK", "No chunk data provided");
            }

            // Validate chunk index
            if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
                return badRequest("INVALID_CHUNK_INDEX", "Invalid chunk index: " + chunkIndex);
            }

            // Check if chunk already received (idempotent)
            if (session.hasChunk(chunkIndex)) {
                log.info("Chunk {} already received for upload {}, skipping", chunkIndex, uploadId);
                return Response.ok(createChunkResponse(session, chunkIndex, "Chunk already received")).build();
            }

            // Save chunk to disk
            try (InputStream chunkStream = java.nio.file.Files.newInputStream(chunkFile.uploadedFile())) {
                chunkedUploadService.saveChunk(uploadId, chunkIndex, chunkStream);
            }

            log.debug("Received chunk {} for upload {}, progress: {}/{}",
                    chunkIndex, uploadId, session.getReceivedChunkCount(), session.getTotalChunks());

            return Response.ok(createChunkResponse(session, chunkIndex, null)).build();

        } catch (IllegalStateException e) {
            log.warn("Invalid state for chunk upload: {}", e.getMessage());
            return conflict("INVALID_STATE", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument for chunk upload: {}", e.getMessage());
            return badRequest("INVALID_REQUEST", e.getMessage());
        } catch (IOException e) {
            log.error("Failed to save chunk", e);
            return serverError("CHUNK_SAVE_ERROR", "Failed to save chunk");
        } catch (Exception e) {
            log.error("Failed to process chunk upload", e);
            return serverError("INTERNAL_ERROR", "Failed to process chunk");
        }
    }

    /**
     * Complete a chunked upload and create an import job.
     */
    @POST
    @Path("/{uploadId}/complete")
    public Response completeChunkedUpload(@PathParam("uploadId") UUID uploadId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            // Validate session exists and belongs to user
            Optional<ChunkedUploadSession> sessionOpt = chunkedUploadService.getUploadStatus(uploadId, userId);
            if (sessionOpt.isEmpty()) {
                return notFound("UPLOAD_NOT_FOUND", "Upload session not found");
            }

            ChunkedUploadSession session = sessionOpt.get();

            // Check if all chunks received
            if (!session.isComplete()) {
                return conflict("UPLOAD_INCOMPLETE",
                        "Upload is not complete. Received " + session.getReceivedChunkCount() +
                                "/" + session.getTotalChunks() + " chunks");
            }

            // Check for existing active import job (in case one was created while uploading)
            if (importJobService.hasActiveImportJob(userId)) {
                return conflict("ACTIVE_JOB_EXISTS",
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

            return Response.ok(createJobResponse(job, "Chunked upload completed, import job created")).build();

        } catch (IOException e) {
            log.error("Failed to assemble chunked upload", e);
            return serverError("ASSEMBLY_ERROR", "Failed to assemble uploaded chunks");
        } catch (Exception e) {
            log.error("Failed to complete chunked upload", e);
            return serverError("INTERNAL_ERROR", "Failed to complete chunked upload");
        }
    }

    /**
     * Get the status of a chunked upload session.
     */
    @GET
    @Path("/{uploadId}/status")
    public Response getUploadStatus(@PathParam("uploadId") UUID uploadId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            Optional<ChunkedUploadSession> sessionOpt = chunkedUploadService.getUploadStatus(uploadId, userId);
            if (sessionOpt.isEmpty()) {
                return notFound("UPLOAD_NOT_FOUND", "Upload session not found");
            }

            ChunkedUploadSession session = sessionOpt.get();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("uploadId", session.getUploadId());
            response.put("fileName", session.getFileName());
            response.put("fileSize", session.getFileSize());
            response.put("totalChunks", session.getTotalChunks());
            response.put("receivedChunks", session.getReceivedChunkCount());
            response.put("progress", session.getProgressPercentage());
            response.put("status", session.getStatus().name().toLowerCase(Locale.ENGLISH));
            response.put("isComplete", session.isComplete());
            response.put("isExpired", session.isExpired());
            response.put("expiresAt", session.getExpiresAt());
            response.put("receivedChunkIndices", session.getReceivedChunks());

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Failed to get upload status", e);
            return serverError("INTERNAL_ERROR", "Failed to get upload status");
        }
    }

    /**
     * Abort a chunked upload and cleanup temp files.
     */
    @DELETE
    @Path("/{uploadId}")
    public Response abortUpload(@PathParam("uploadId") UUID uploadId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();

            boolean deleted = chunkedUploadService.abortUpload(uploadId, userId);
            if (!deleted) {
                return notFound("UPLOAD_NOT_FOUND", "Upload session not found");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Upload aborted successfully");

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Failed to abort upload", e);
            return serverError("INTERNAL_ERROR", "Failed to abort upload");
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

    private ImportJobResponse createJobResponse(ImportJob job, String message) {
        ImportJobResponse response = new ImportJobResponse();
        response.setSuccess(true);
        response.setImportJobId(job.getJobId());
        response.setStatus(job.getStatus().name().toLowerCase(Locale.ENGLISH));
        response.setUploadedFileName(job.getUploadedFileName());
        response.setFileSizeBytes(job.getFileSizeBytes());
        response.setDetectedDataTypes(job.getDetectedDataTypes());
        response.setEstimatedProcessingTime(job.getEstimatedProcessingTime());
        response.setMessage(message);
        return response;
    }

    private Map<String, Object> createChunkResponse(ChunkedUploadSession session, int chunkIndex, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("chunkIndex", chunkIndex);
        response.put("receivedChunks", session.getReceivedChunkCount());
        response.put("totalChunks", session.getTotalChunks());
        response.put("progress", session.getProgressPercentage());
        response.put("isComplete", session.isComplete());
        if (message != null) {
            response.put("message", message);
        }
        return response;
    }

    // ==================== ERROR RESPONSE HELPERS ====================

    private Response badRequest(String code, String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(createErrorResponse(code, message))
                .build();
    }

    private Response conflict(String code, String message) {
        return Response.status(Response.Status.CONFLICT)
                .entity(createErrorResponse(code, message))
                .build();
    }

    private Response notFound(String code, String message) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(createErrorResponse(code, message))
                .build();
    }

    private Response gone(String code, String message) {
        return Response.status(Response.Status.GONE)
                .entity(createErrorResponse(code, message))
                .build();
    }

    private Response serverError(String code, String message) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(createErrorResponse(code, message))
                .build();
    }

    private Map<String, Object> createErrorResponse(String code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);

        return response;
    }
}
