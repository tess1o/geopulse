package org.github.tess1o.geopulse.streaming.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.streaming.model.dto.BoatSetupStartResponseDTO;
import org.github.tess1o.geopulse.streaming.model.dto.BoatSetupStatusDTO;
import org.github.tess1o.geopulse.streaming.service.boat.BoatSetupService;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/boat/setup")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
@Tag(name = "User: Trips and Planning", description = "Run and monitor boat setup analysis.")
public class BoatSetupResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    BoatSetupService boatSetupService;

    @GET
    @Path("/status")
    @RolesAllowed({"USER", "ADMIN"})
    public Response getStatus() {
        UUID userId = currentUserService.getCurrentUserId();
        BoatSetupStatusDTO status = boatSetupService.getStatus(userId);
        return Response.ok(ApiResponse.success(status)).build();
    }

    @POST
    @Path("/start")
    @RolesAllowed({"USER", "ADMIN"})
    public Response startSetup() {
        UUID userId = currentUserService.getCurrentUserId();
        BoatSetupStartResponseDTO response = boatSetupService.startSetup(userId);
        return Response.ok(ApiResponse.success(response)).build();
    }

    @GET
    @Path("/jobs/{jobId}")
    @RolesAllowed({"USER", "ADMIN"})
    public Response getJob(@PathParam("jobId") UUID jobId) {
        UUID userId = currentUserService.getCurrentUserId();
        BoatSetupStatusDTO status = boatSetupService.getJobStatus(userId, jobId)
                .orElseThrow(() -> new NotFoundException("Boat setup job not found"));
        return Response.ok(ApiResponse.success(status)).build();
    }
}
