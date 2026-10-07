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
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.BOAT_SETUP_JOB_NOT_FOUND;

@Path(ApiPaths.TRIP_PLANNING + "/boat-setup")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = ApiTags.TIMELINE)
public class BoatSetupResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    BoatSetupService boatSetupService;

    @GET
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Get boat detection setup status",
            description = "Returns whether boat detection is ready for the signed-in user: whether the water dataset "
                    + "is imported on the server and how many of the user's GPS points have been checked against "
                    + "it, or the progress of a running setup job.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public BoatSetupStatusDTO getStatus() {
        UUID userId = currentUserService.getCurrentUserId();
        return boatSetupService.getStatus(userId);
    }

    @POST
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Set up boat detection",
            description = "Starts a background job that prepares boat detection: it downloads and imports the water "
                    + "dataset if the server does not have it yet, checks the user's GPS points against it so trips "
                    + "over water can be classified as `BOAT`, and reclassifies existing trips. Poll `GET "
                    + "/api/v1/trip-planning/boat-setup/jobs/{jobId}` for progress.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public BoatSetupStartResponseDTO startSetup() {
        UUID userId = currentUserService.getCurrentUserId();
        return boatSetupService.startSetup(userId);
    }

    @GET
    @Path("/jobs/{jobId}")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Get boat detection setup job",
            description = "Returns the progress of a boat detection setup job.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public BoatSetupStatusDTO getJob(
            @Parameter(description = "Setup job ID.")
            @PathParam("jobId") UUID jobId) {
        UUID userId = currentUserService.getCurrentUserId();
        return boatSetupService.getJobStatus(userId, jobId)
                .orElseThrow(() -> new GeoPulseException(BOAT_SETUP_JOB_NOT_FOUND, "Boat setup job not found"));
    }
}
