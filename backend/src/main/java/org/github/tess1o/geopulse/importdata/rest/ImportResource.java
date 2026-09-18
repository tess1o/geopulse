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
import org.github.tess1o.geopulse.shared.api.SliceResponse;

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
@Path("/imports")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "User: Import and Export", description = "Read, monitor, and delete import jobs.")
public class ImportResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ImportJobService importJobService;

    @GET
    public SliceResponse<ImportJobResponse> getImportJobs(@QueryParam("page") @DefaultValue("0") int page,
                                                          @QueryParam("size") @DefaultValue("10") int size) {
        if (size < 1 || size > 100) {
            throw problem(INVALID_LIMIT, "Limit must be between 1 and 100", Map.of("min", 1, "max", 100));
        }
        if (page < 0) {
            throw problem(INVALID_PAGE, "Page must not be negative", Map.of("min", 0));
        }

        UUID userId = currentUserService.getCurrentUserId();
        List<ImportJob> jobs = importJobService.getUserImportJobs(userId, size + 1, page * size);
        boolean hasNext = jobs.size() > size;
        List<ImportJobResponse> items = jobs.stream().limit(size).map(ImportJobResponse::from).toList();
        return new SliceResponse<>(items, page, size, hasNext);
    }

    @GET
    @Path("/{importJobId}")
    public ImportJobResponse getImportStatus(@PathParam("importJobId") UUID importJobId) {
        ImportJob job = importJobService.getImportJob(importJobId, currentUserService.getCurrentUserId());
        if (job == null) {
            throw problem(IMPORT_JOB_NOT_FOUND, "Import job not found",
                    Map.of("importJobId", importJobId.toString()));
        }
        return ImportJobResponse.from(job);
    }

    @DELETE
    @Path("/{importJobId}")
    public void deleteImportJob(@PathParam("importJobId") UUID importJobId) {
        if (!importJobService.deleteImportJob(importJobId, currentUserService.getCurrentUserId())) {
            throw problem(IMPORT_JOB_NOT_FOUND, "Import job not found",
                    Map.of("importJobId", importJobId.toString()));
        }
    }
}
