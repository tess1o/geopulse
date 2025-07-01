package org.github.tess1o.geopulse.importdata.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportJobResponse;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.ImportService;
import org.jboss.resteasy.reactive.MultipartForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Path("/api/import")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class ImportResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ImportService importService;

    @Inject
    ObjectMapper objectMapper;

    @POST
    @Path("/owntracks/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadOwnTracksImportFile(@MultipartForm ImportUploadForm form) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            log.info("Received OwnTracks import request for user: {}", userId);

            // Validate file
            if (form.file == null || form.file.size() == 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("INVALID_FILE", "No file provided"))
                        .build();
            }

            // Check file size (max 100MB)
            if (form.file.size() > 100 * 1024 * 1024) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("FILE_TOO_LARGE", "File size exceeds 100MB limit"))
                        .build();
            }

            // Validate file extension (should be .json)
            String fileName = form.file.fileName() != null ? form.file.fileName() : "owntracks-import.json";
            if (!fileName.toLowerCase(Locale.ENGLISH).endsWith(".json")) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("INVALID_FILE_TYPE", "Only JSON files are supported for OwnTracks import"))
                        .build();
            }

            // Read file content
            byte[] fileContent;
            try {
                fileContent = Files.readAllBytes(form.file.uploadedFile());
            } catch (IOException e) {
                log.error("Failed to read uploaded file", e);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("FILE_READ_ERROR", "Failed to read uploaded file"))
                        .build();
            }

            // Parse options
            ImportOptions options;
            try {
                options = objectMapper.readValue(form.options, ImportOptions.class);
                // Force format to be owntracks
                options.setImportFormat("owntracks");
            } catch (Exception e) {
                log.error("Failed to parse import options", e);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("INVALID_OPTIONS", "Invalid import options format"))
                        .build();
            }

            // Create import job
            ImportJob job = importService.createOwnTracksImportJob(userId, options, fileName, fileContent);

            // Create response
            ImportJobResponse response = new ImportJobResponse();
            response.setSuccess(true);
            response.setImportJobId(job.getJobId());
            response.setStatus(job.getStatus().name().toLowerCase(Locale.ENGLISH));
            response.setUploadedFileName(job.getUploadedFileName());
            response.setFileSizeBytes(job.getFileSizeBytes());
            response.setDetectedDataTypes(job.getDetectedDataTypes());
            response.setEstimatedProcessingTime(job.getEstimatedProcessingTime());
            response.setMessage("OwnTracks import job created successfully");

            return Response.ok(response).build();

        } catch (IllegalStateException e) {
            return Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .entity(createErrorResponse("RATE_LIMIT_EXCEEDED", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create OwnTracks import job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("INTERNAL_ERROR", "Failed to create OwnTracks import job"))
                    .build();
        }
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadImportFile(@MultipartForm ImportUploadForm form) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            log.info("Received import request for user: {}", userId);

            // Validate file
            if (form.file == null || form.file.size() == 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("INVALID_FILE", "No file provided"))
                        .build();
            }

            // Check file size (max 100MB)
            if (form.file.size() > 100 * 1024 * 1024) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("FILE_TOO_LARGE", "File size exceeds 100MB limit"))
                        .build();
            }

            // Read file content
            byte[] fileContent;
            try {
                fileContent = Files.readAllBytes(form.file.uploadedFile());
            } catch (IOException e) {
                log.error("Failed to read uploaded file", e);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("FILE_READ_ERROR", "Failed to read uploaded file"))
                        .build();
            }

            // Parse options
            ImportOptions options;
            try {
                options = objectMapper.readValue(form.options, ImportOptions.class);
            } catch (Exception e) {
                log.error("Failed to parse import options", e);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("INVALID_OPTIONS", "Invalid import options format"))
                        .build();
            }

            // Validate options
            if (options.getDataTypes() == null || options.getDataTypes().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("INVALID_OPTIONS", "Data types are required"))
                        .build();
            }

            // Create import job
            String fileName = form.file.fileName() != null ? form.file.fileName() : "import-" + System.currentTimeMillis() + ".zip";
            ImportJob job = importService.createImportJob(userId, options, fileName, fileContent);

            // Create response
            ImportJobResponse response = new ImportJobResponse();
            response.setSuccess(true);
            response.setImportJobId(job.getJobId());
            response.setStatus(job.getStatus().name().toLowerCase(Locale.ENGLISH));
            response.setUploadedFileName(job.getUploadedFileName());
            response.setFileSizeBytes(job.getFileSizeBytes());
            response.setDetectedDataTypes(job.getDetectedDataTypes());
            response.setEstimatedProcessingTime(job.getEstimatedProcessingTime());
            response.setMessage("Import job created successfully");

            return Response.ok(response).build();

        } catch (IllegalStateException e) {
            return Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .entity(createErrorResponse("RATE_LIMIT_EXCEEDED", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create import job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("INTERNAL_ERROR", "Failed to create import job"))
                    .build();
        }
    }

    @GET
    @Path("/status/{importJobId}")
    public Response getImportStatus(@PathParam("importJobId") UUID importJobId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            ImportJob job = importService.getImportJob(importJobId, userId);

            if (job == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(createErrorResponse("IMPORT_NOT_FOUND", "Import job not found"))
                        .build();
            }

            ImportJobResponse response = new ImportJobResponse();
            response.setSuccess(true);
            response.setImportJobId(job.getJobId());
            response.setStatus(job.getStatus().name().toLowerCase(Locale.ENGLISH));
            response.setUploadedFileName(job.getUploadedFileName());
            response.setFileSizeBytes(job.getFileSizeBytes());
            response.setDetectedDataTypes(job.getDetectedDataTypes());
            response.setProgress(job.getProgress());
            response.setCreatedAt(job.getCreatedAt());
            response.setCompletedAt(job.getCompletedAt());
            response.setError(job.getError());

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Failed to get import status", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("INTERNAL_ERROR", "Failed to get import status"))
                    .build();
        }
    }

    @DELETE
    @Path("/jobs/{importJobId}")
    public Response deleteImportJob(@PathParam("importJobId") UUID importJobId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            boolean deleted = importService.deleteImportJob(importJobId, userId);

            if (!deleted) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(createErrorResponse("IMPORT_NOT_FOUND", "Import job not found"))
                        .build();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Import job deleted successfully");

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Failed to delete import job", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("INTERNAL_ERROR", "Failed to delete import job"))
                    .build();
        }
    }

    // Helper method to create error responses
    private Map<String, Object> createErrorResponse(String code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", error);
        
        return response;
    }

    // Form data class for multipart upload
    public static class ImportUploadForm {
        @RestForm("file")
        public FileUpload file;

        @RestForm("options")
        @PartType(MediaType.TEXT_PLAIN)
        public String options;
    }
}