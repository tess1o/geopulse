package org.github.tess1o.geopulse.importdata.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.importdata.model.DebugImportJobResponse;
import org.github.tess1o.geopulse.importdata.model.DebugImportRequest;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.DebugImportService;
import org.github.tess1o.geopulse.importdata.service.DebugImportStrategy;
import org.github.tess1o.geopulse.importdata.service.ImportJobService;
import org.github.tess1o.geopulse.importdata.service.ImportTempFileService;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.Locale;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.DEBUG_IMPORT_FAILED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.IMPORT_ACTIVE_JOB_CONFLICT;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.IMPORT_JOB_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_DEBUG_IMPORT;

@Path("/debug-imports")
@Authenticated
@Slf4j
@Tag(name = ApiTags.IMPORT)
public class DebugImportResource {

    @Inject
    ImportJobService importJobService;

    @Inject
    ImportTempFileService tempFileService;

    @Inject
    DebugImportService debugImportService;

    @Inject
    CurrentUserService currentUserService;

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Start a debug import",
            description = "Starts a background import of a ZIP archive created by `POST /api/v1/exports/debug`, which "
                    + "is used to reproduce timeline issues. By default, the user's existing data is deleted first and "
                    + "the timeline settings and timezone from the archive are applied. Poll `GET "
                    + "/api/v1/debug-imports/{importJobId}` until the job is `completed`; its report compares the "
                    + "regenerated timeline with the one in the archive. Intended for troubleshooting, not for regular "
                    + "imports.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public DebugImportJobResponse uploadDebugData(
            @FormParam("file") FileUpload file,
            @Parameter(description = "Delete the user's existing GPS, timeline and favorites data before importing. "
                    + "Defaults to `true`.")
            @FormParam("clearExistingData") @DefaultValue("true") boolean clearExistingData,
            @Parameter(description = "Apply the timeline settings stored in the archive. Defaults to `true`.")
            @FormParam("updateTimelineConfig") @DefaultValue("true") boolean updateTimelineConfig,
            @Parameter(description = "Use the timezone stored in the archive. Defaults to `true`.")
            @FormParam("updateTimezone") @DefaultValue("true") boolean updateTimezone) {

        if (file == null || file.uploadedFile() == null) {
            throw new GeoPulseException(INVALID_DEBUG_IMPORT, "No file uploaded");
        }

        if (file.fileName() == null || !file.fileName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new GeoPulseException(INVALID_DEBUG_IMPORT, "File must be a ZIP archive");
        }

        UUID userId = currentUserService.getCurrentUserId();
        if (importJobService.hasActiveImportJob(userId)) {
            throw new GeoPulseException(IMPORT_ACTIVE_JOB_CONFLICT,
                    "An import job is already in progress. Please wait for it to complete.");
        }
        log.info("Received debug import request from user {}: file={}, clearData={}, updateConfig={}, "
                        + "updateTimezone={}", userId, file.fileName(), clearExistingData, updateTimelineConfig,
                updateTimezone);

        try {
            ImportOptions options = new ImportOptions();
            options.setImportFormat(DebugImportStrategy.FORMAT);
            options.setClearDataBeforeImport(clearExistingData);

            // The upload is kept on disk: a large export is never read into memory at once
            ImportJob job = new ImportJob(userId, options, file.fileName(), new byte[0]);
            job.setTempFilePath(tempFileService.copyFileToTemp(file.uploadedFile(), job.getJobId(), file.fileName()));
            job.setFileSizeBytes(file.size());
            job.setDebugImportRequest(new DebugImportRequest(clearExistingData, updateTimelineConfig, updateTimezone));
            try {
                // Reject a file that is not a debug export now, rather than in a job that fails later
                debugImportService.validate(job);
            } catch (IllegalArgumentException e) {
                tempFileService.cleanupTempFile(job);
                throw new GeoPulseException(INVALID_DEBUG_IMPORT, INVALID_DEBUG_IMPORT.title(), e);
            }
            importJobService.registerJob(job);
            return DebugImportJobResponse.from(job);
        } catch (java.io.IOException e) {
            throw new GeoPulseException(DEBUG_IMPORT_FAILED, "Debug import failed", e);
        }
    }

    @GET
    @Path("/{importJobId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get a debug import job",
            description = "Returns the status and progress of a debug import. When it is `completed`, the response "
                    + "includes a report: the user's description of the problem, how the regenerated timeline "
                    + "compares with the one in the archive, and reasons the two may differ other than a bug.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public DebugImportJobResponse getDebugImport(
            @Parameter(description = "Import job ID.")
            @PathParam("importJobId") UUID importJobId) {
        ImportJob job = importJobService.getImportJob(importJobId, currentUserService.getCurrentUserId());
        if (job == null || !DebugImportStrategy.FORMAT.equals(job.getOptions().getImportFormat())) {
            throw new GeoPulseException(IMPORT_JOB_NOT_FOUND, "Debug import job not found");
        }
        return DebugImportJobResponse.from(job);
    }
}
