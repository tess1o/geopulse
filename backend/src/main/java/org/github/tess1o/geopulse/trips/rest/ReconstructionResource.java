package org.github.tess1o.geopulse.trips.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionCommitResponseDto;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionPreviewDto;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionRequestDto;
import org.github.tess1o.geopulse.shared.api.ApiPaths;
import org.github.tess1o.geopulse.trips.service.TripReconstructionService;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.Operation;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TRIP_RECONSTRUCTION;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_NOT_FOUND;

@Path(ApiPaths.TRIP_PLANNING + "/reconstructions")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.TRIP_PLANNING)
public class ReconstructionResource {

    private final TripReconstructionService tripReconstructionService;
    private final CurrentUserService currentUserService;

    public ReconstructionResource(TripReconstructionService tripReconstructionService,
                                  CurrentUserService currentUserService) {
        this.tripReconstructionService = tripReconstructionService;
        this.currentUserService = currentUserService;
    }

    @POST
    @Path("/preview")
    @Operation(summary = "Preview a trip reconstruction",
            description = "Checks a reconstruction request and returns how many GPS points it would create, and for "
                    + "which time range. A reconstruction describes missing parts of a trip as a list of stays and "
                    + "movements (with optional waypoints) so GeoPulse can generate GPS points for them. Nothing is "
                    + "saved.")
    public TripReconstructionPreviewDto preview(@Valid TripReconstructionRequestDto request) {
        try {
            return tripReconstructionService.preview(currentUserService.getCurrentUserId(), request);
        } catch (NotFoundException e) {
            throw new GeoPulseException(TRIP_NOT_FOUND, "Trip not found", e);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    @POST
    @Path("/commit")
    @Operation(summary = "Save a trip reconstruction",
            description = "Generates GPS points from the reconstruction segments and stores them for the trip owner. "
                    + "Points that already exist are skipped. Then starts a timeline regeneration from the earliest "
                    + "new point and returns its job ID.")
    public TripReconstructionCommitResponseDto commit(@Valid TripReconstructionRequestDto request) {
        try {
            return tripReconstructionService.commit(currentUserService.getCurrentUserId(), request);
        } catch (NotFoundException e) {
            throw new GeoPulseException(TRIP_NOT_FOUND, "Trip not found", e);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    private static GeoPulseException invalid(IllegalArgumentException exception) {
        return new GeoPulseException(INVALID_TRIP_RECONSTRUCTION, "Invalid reconstruction request", exception);
    }
}
