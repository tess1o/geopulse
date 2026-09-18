package org.github.tess1o.geopulse.trips.rest;

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

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TRIP_RECONSTRUCTION;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path(ApiPaths.TRIP_PLANNING + "/reconstructions")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Trips and Planning", description = "Preview and commit timeline reconstruction changes.")
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
    public TripReconstructionPreviewDto preview(@Valid TripReconstructionRequestDto request) {
        try {
            return tripReconstructionService.preview(currentUserService.getCurrentUserId(), request);
        } catch (NotFoundException e) {
            throw problem(TRIP_NOT_FOUND, "Trip not found");
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    @POST
    @Path("/commit")
    public TripReconstructionCommitResponseDto commit(@Valid TripReconstructionRequestDto request) {
        try {
            return tripReconstructionService.commit(currentUserService.getCurrentUserId(), request);
        } catch (NotFoundException e) {
            throw problem(TRIP_NOT_FOUND, "Trip not found");
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    private static io.quarkiverse.httpproblem.HttpProblem invalid(IllegalArgumentException exception) {
        String detail = exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Invalid reconstruction request"
                : exception.getMessage();
        return problem(INVALID_TRIP_RECONSTRUCTION, detail);
    }
}
