package org.github.tess1o.geopulse.admin.rest;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.github.tess1o.geopulse.admin.service.BackupMaintenanceService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

@Path("/api/maintenance/status")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
public class MaintenanceStatusResource {
    @Inject BackupMaintenanceService maintenance;
    @GET
    public Response status() { return Response.ok(ApiResponse.success(maintenance.publicStatus())).header("Cache-Control","no-store").build(); }
}
