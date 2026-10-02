package org.github.tess1o.geopulse.admin.rest;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.admin.dto.backup.MaintenanceStatusDto;
import org.github.tess1o.geopulse.admin.service.BackupMaintenanceService;

@Path("/system/maintenance")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "User: System", description = "Public system status endpoints.")
public class MaintenanceStatusResource {
    @Inject BackupMaintenanceService maintenance;
    @GET
    @APIResponse(responseCode = "200", description = "Public restore maintenance status",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = MaintenanceStatusDto.class)))
    public Response status() {
        return Response.ok(maintenance.publicStatus())
                .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                .header("Pragma", "no-cache")
                .build();
    }
}
