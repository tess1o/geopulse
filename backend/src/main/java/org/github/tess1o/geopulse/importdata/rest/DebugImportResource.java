package org.github.tess1o.geopulse.importdata.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.importdata.model.DebugImportRequest;
import org.github.tess1o.geopulse.importdata.service.DebugImportService;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.nio.file.Files;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.DEBUG_IMPORT_FAILED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_DEBUG_IMPORT;

@Path("/debug-imports")
@Authenticated
@Slf4j
@Tag(name = ApiTags.IMPORT)
public class DebugImportResource {

    @Inject
    DebugImportService debugImportService;

    @Inject
    CurrentUserService currentUserService;

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Import a debug export",
            description = "Imports a ZIP archive created by `POST /api/v1/exports/debug`, which is used to reproduce "
                    + "timeline issues. By default, the user's existing data is deleted first and the timeline "
                    + "settings from the archive are applied. Intended for troubleshooting, not for regular imports.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public void uploadDebugData(
            @FormParam("file") FileUpload file,
            @Parameter(description = "Delete the user's existing GPS and timeline data before importing. Defaults to "
                    + "`true`.")
            @FormParam("clearExistingData") @DefaultValue("true") boolean clearExistingData,
            @Parameter(description = "Apply the timeline settings stored in the archive. Defaults to `true`.")
            @FormParam("updateTimelineConfig") @DefaultValue("true") boolean updateTimelineConfig) {

        if (file == null || file.uploadedFile() == null) {
            throw new GeoPulseException(INVALID_DEBUG_IMPORT, "No file uploaded");
        }

        if (file.fileName() == null || !file.fileName().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            throw new GeoPulseException(INVALID_DEBUG_IMPORT, "File must be a ZIP archive");
        }

        try {
            UUID userId = currentUserService.getCurrentUserId();
            log.info("Received debug import request from user {}: file={}, clearData={}, updateConfig={}",
                    userId, file.fileName(), clearExistingData, updateTimelineConfig);

            // Read file contents
            byte[] zipData = Files.readAllBytes(file.uploadedFile());

            // Create import request
            DebugImportRequest request = new DebugImportRequest(clearExistingData, updateTimelineConfig);

            // Import data
            debugImportService.importDebugData(userId, zipData, request);

        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_DEBUG_IMPORT, INVALID_DEBUG_IMPORT.title(), e);
        } catch (java.io.IOException e) {
            throw new GeoPulseException(DEBUG_IMPORT_FAILED, "Debug import failed", e);
        }
    }
}
