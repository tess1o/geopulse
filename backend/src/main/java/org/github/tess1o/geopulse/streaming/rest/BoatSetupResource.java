package org.github.tess1o.geopulse.streaming.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiPaths;
import org.github.tess1o.geopulse.streaming.model.dto.BoatSetupStartResponseDTO;
import org.github.tess1o.geopulse.streaming.model.dto.BoatSetupStatusDTO;
import org.github.tess1o.geopulse.streaming.service.boat.BoatSetupService;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.BOAT_SETUP_JOB_NOT_FOUND;

@Path(ApiPaths.TRIP_PLANNING + "/boat-setup")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "User: Trips and Planning", description = "Run and monitor boat setup analysis.")
public class BoatSetupResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    BoatSetupService boatSetupService;

    @GET
    @RolesAllowed({"USER", "ADMIN"})
    public BoatSetupStatusDTO getStatus() {
        UUID userId = currentUserService.getCurrentUserId();
        return boatSetupService.getStatus(userId);
    }

    @POST
    @RolesAllowed({"USER", "ADMIN"})
    public BoatSetupStartResponseDTO startSetup() {
        UUID userId = currentUserService.getCurrentUserId();
        return boatSetupService.startSetup(userId);
    }

    @GET
    @Path("/jobs/{jobId}")
    @RolesAllowed({"USER", "ADMIN"})
    public BoatSetupStatusDTO getJob(@PathParam("jobId") UUID jobId) {
        UUID userId = currentUserService.getCurrentUserId();
        return boatSetupService.getJobStatus(userId, jobId)
                .orElseThrow(() -> new GeoPulseException(BOAT_SETUP_JOB_NOT_FOUND, "Boat setup job not found"));
    }
}
