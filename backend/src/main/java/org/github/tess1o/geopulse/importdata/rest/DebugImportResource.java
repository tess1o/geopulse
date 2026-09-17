package org.github.tess1o.geopulse.importdata.rest;

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

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.DEBUG_IMPORT_FAILED;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_DEBUG_IMPORT;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/api/import/debug")
@Slf4j
@Tag(name = "User: Import and Export", description = "Upload debug import data.")
public class DebugImportResource {

    @Inject
    DebugImportService debugImportService;

    @Inject
    CurrentUserService currentUserService;

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public void uploadDebugData(
            @FormParam("file") FileUpload file,
            @FormParam("clearExistingData") @DefaultValue("true") boolean clearExistingData,
            @FormParam("updateTimelineConfig") @DefaultValue("true") boolean updateTimelineConfig) {

        if (file == null || file.uploadedFile() == null) {
            throw problem(INVALID_DEBUG_IMPORT, "No file uploaded");
        }

        if (file.fileName() == null || !file.fileName().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            throw problem(INVALID_DEBUG_IMPORT, "File must be a ZIP archive");
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
            log.warn("Invalid debug import request: {}", e.getMessage());
            throw problem(INVALID_DEBUG_IMPORT, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to import debug data", e);
            throw problem(DEBUG_IMPORT_FAILED, "Debug import failed");
        }
    }
}
