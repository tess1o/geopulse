package org.github.tess1o.geopulse.admin.rest;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.admin.dto.backup.MaintenanceStatusDto;
import org.github.tess1o.geopulse.admin.service.BackupMaintenanceService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

@Path("/system/maintenance")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = ApiTags.SYSTEM)
public class MaintenanceStatusResource {
    @Inject BackupMaintenanceService maintenance;
    @GET
    @APIResponse(responseCode = "200", description = "Public restore maintenance status",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = MaintenanceStatusDto.class)))
    @Operation(summary = "Get maintenance status",
            description = "Returns whether the server is in maintenance mode, for example while a backup is being "
                    + "restored, so clients can show a notice and pause requests.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public Response status() {
        return Response.ok(maintenance.publicStatus())
                .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                .header("Pragma", "no-cache")
                .build();
    }
}
