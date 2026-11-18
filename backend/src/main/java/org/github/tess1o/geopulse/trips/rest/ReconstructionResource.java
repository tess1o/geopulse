package org.github.tess1o.geopulse.trips.rest;

import org.github.tess1o.geopulse.auth.model.ApiTokenScope;
import org.github.tess1o.geopulse.auth.security.RequiresApiTokenScope;
import org.github.tess1o.geopulse.shared.api.ApiErrorCode;
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
import org.github.tess1o.geopulse.streaming.exception.TimelineGenerationLockException;
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
    @RequiresApiTokenScope(ApiTokenScope.READ)
    @Operation(summary = "Preview a trip reconstruction",
            description = "Runs the correction fold for the stays and movements a reconstruction describes and "
                    + "returns the resulting events, warnings and a preview token. Nothing is saved and no GPS points "
                    + "are created.")
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
            description = "Saves the reconstruction segments as one correction group scoped to the planned trip's "
                    + "dates and starts a timeline regeneration. No GPS points are created or changed.")
    public TripReconstructionCommitResponseDto commit(@Valid TripReconstructionRequestDto request) {
        try {
            return tripReconstructionService.commit(currentUserService.getCurrentUserId(), request);
        } catch (NotFoundException e) {
            throw new GeoPulseException(TRIP_NOT_FOUND, "Trip not found", e);
        } catch (TimelineGenerationLockException e) {
            throw new GeoPulseException(ApiErrorCode.TIMELINE_REGENERATION_IN_PROGRESS,
                    "Timeline update in progress. Try again when it finishes.", e);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    private static GeoPulseException invalid(IllegalArgumentException exception) {
        return new GeoPulseException(INVALID_TRIP_RECONSTRUCTION, "Invalid reconstruction request", exception);
    }
}
