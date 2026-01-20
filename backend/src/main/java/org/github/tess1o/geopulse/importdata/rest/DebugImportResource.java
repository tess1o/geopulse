package org.github.tess1o.geopulse.importdata.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.importdata.model.DebugImportRequest;
import org.github.tess1o.geopulse.importdata.service.DebugImportService;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

@Path("/api/import/debug")
@Slf4j
public class DebugImportResource {

    @Inject
    DebugImportService debugImportService;

    @Inject
    CurrentUserService currentUserService;

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadDebugData(
            @FormParam("file") FileUpload file,
            @FormParam("clearExistingData") @DefaultValue("true") boolean clearExistingData,
            @FormParam("updateTimelineConfig") @DefaultValue("true") boolean updateTimelineConfig) {

        try {
            UUID userId = currentUserService.getCurrentUserId();

            log.info("Received debug import request from user {}: file={}, clearData={}, updateConfig={}",
                userId, file.fileName(), clearExistingData, updateTimelineConfig);

            // Validate file
            if (file == null || file.uploadedFile() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", Map.of("message", "No file uploaded")))
                    .build();
            }

            if (!file.fileName().endsWith(".zip")) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", Map.of("message", "File must be a ZIP archive")))
                    .build();
            }

            // Read file contents
            byte[] zipData = Files.readAllBytes(file.uploadedFile());

            // Create import request
            DebugImportRequest request = new DebugImportRequest(clearExistingData, updateTimelineConfig);

            // Import data
            debugImportService.importDebugData(userId, zipData, request);

            return Response.ok()
                .entity(Map.of("success", true, "message", "Debug data imported successfully"))
                .build();

        } catch (IllegalArgumentException e) {
            log.error("Invalid debug import request", e);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", Map.of("message", e.getMessage())))
                .build();
        } catch (Exception e) {
            log.error("Failed to import debug data", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", Map.of("message", "Import failed: " + e.getMessage())))
                .build();
        }
    }
}
