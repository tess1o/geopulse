package org.github.tess1o.geopulse.importdata.rest;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportJobResponse;
import org.github.tess1o.geopulse.importdata.service.ImportJobService;

import java.util.*;

/**
 * REST resource for managing import jobs.
 * File uploads are handled by UnifiedImportResource.
 */
@Path("/api/import")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class ImportResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ImportJobService importJobService;

    @GET
    @Path("/jobs")
    public Response getImportJobs(@QueryParam("limit") @DefaultValue("10") int limit,
                                   @QueryParam("offset") @DefaultValue("0") int offset) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            List<ImportJob> userJobs = importJobService.getUserImportJobs(userId, limit, offset);

            // Convert to responses
            List<ImportJobResponse> jobResponses = userJobs.stream()
                    .map(job -> {
                        ImportJobResponse response = new ImportJobResponse();
                        response.setSuccess(true);
                        response.setImportJobId(job.getJobId());
                        response.setStatus(job.getStatus().name().toLowerCase(Locale.ENGLISH));
                        response.setUploadedFileName(job.getUploadedFileName());
                        response.setFileSizeBytes(job.getFileSizeBytes());
                        response.setDetectedDataTypes(job.getDetectedDataTypes());
                        response.setProgress(job.getProgress());
                        response.setProgressMessage(job.getProgressMessage());
                        response.setCreatedAt(job.getCreatedAt());
                        response.setCompletedAt(job.getCompletedAt());
                        response.setError(job.getError());
                        response.setTimelineJobId(job.getTimelineJobId());
                        return response;
                    })
                    .toList();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("jobs", jobResponses);

            return Response.ok(result).build();

        } catch (Exception e) {
            log.error("Failed to get import jobs", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("INTERNAL_ERROR", "Failed to get import jobs"))
                    .build();
        }
    }

    @GET
    @Path("/status/{importJobId}")
    public Response getImportStatus(@PathParam("importJobId") UUID importJobId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            ImportJob job = importJobService.getImportJob(importJobId, userId);

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
            response.setProgressMessage(job.getProgressMessage());
            response.setCreatedAt(job.getCreatedAt());
            response.setCompletedAt(job.getCompletedAt());
            response.setError(job.getError());
            response.setTimelineJobId(job.getTimelineJobId());

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
            boolean deleted = importJobService.deleteImportJob(importJobId, userId);

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
}
