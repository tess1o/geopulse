package org.github.tess1o.geopulse.importdata.rest;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportJobResponse;
import org.github.tess1o.geopulse.importdata.model.ImportJobsResponse;
import org.github.tess1o.geopulse.importdata.service.ImportJobService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

/**
 * REST resource for managing import jobs.
 * File uploads are handled by ImportUploadResource.
 */
@Path("/api/import")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "User: Import and Export", description = "Read, monitor, and delete import jobs.")
public class ImportResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ImportJobService importJobService;

    @GET
    @Path("/jobs")
    public ImportJobsResponse getImportJobs(@QueryParam("limit") @DefaultValue("10") int limit,
                                             @QueryParam("offset") @DefaultValue("0") int offset) {
        if (limit < 1 || limit > 100) {
            throw problem(INVALID_LIMIT, "Limit must be between 1 and 100", Map.of("min", 1, "max", 100));
        }
        if (offset < 0) {
            throw problem(INVALID_PAGE, "Offset must not be negative", Map.of("min", 0));
        }

        UUID userId = currentUserService.getCurrentUserId();
        List<ImportJob> jobs = importJobService.getUserImportJobs(userId, limit + 1, offset);
        boolean hasNext = jobs.size() > limit;
        List<ImportJobResponse> items = jobs.stream().limit(limit).map(ImportJobResponse::from).toList();
        return new ImportJobsResponse(items, limit, offset, hasNext);
    }

    @GET
    @Path("/status/{importJobId}")
    public ImportJobResponse getImportStatus(@PathParam("importJobId") UUID importJobId) {
        ImportJob job = importJobService.getImportJob(importJobId, currentUserService.getCurrentUserId());
        if (job == null) {
            throw problem(IMPORT_JOB_NOT_FOUND, "Import job not found",
                    Map.of("importJobId", importJobId.toString()));
        }
        return ImportJobResponse.from(job);
    }

    @DELETE
    @Path("/jobs/{importJobId}")
    public void deleteImportJob(@PathParam("importJobId") UUID importJobId) {
        if (!importJobService.deleteImportJob(importJobId, currentUserService.getCurrentUserId())) {
            throw problem(IMPORT_JOB_NOT_FOUND, "Import job not found",
                    Map.of("importJobId", importJobId.toString()));
        }
    }
}
