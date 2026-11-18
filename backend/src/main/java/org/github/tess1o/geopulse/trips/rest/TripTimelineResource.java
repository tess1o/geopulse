package org.github.tess1o.geopulse.trips.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.streaming.model.dto.TripClassificationDetailsDTO;
import org.github.tess1o.geopulse.streaming.service.TripClassificationDetailsService;

import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_NOT_FOUND;

/**
 * Read-only classification details for a timeline trip.
 *
 * <p>The class template deliberately declares only the literal sub-paths below and no
 * root method, so {@code /trips/{tripId}/timeline} itself stays owned by
 * {@code TripWorkspaceDataResource} and {@code /trips/{id}} by {@code TripResource}.</p>
 */
@Path("/trips/{tripId}/timeline")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@RequestScoped
@Tag(name = ApiTags.TIMELINE_CORRECTIONS)
public class TripTimelineResource {

    @Inject
    CurrentUserService currentUserService;
    @Inject
    TripClassificationDetailsService tripClassificationDetailsService;
    @GET
    @Path("/classification")
    @Operation(summary = "Explain a movement type",
            description = "Explains how the movement type of a timeline trip was detected: the speed and distance "
                    + "figures used and the thresholds of each movement type.")
    public TripClassificationDetailsDTO getTripClassificationDetails(
            @Parameter(description = "ID of a trip (movement) on the timeline, as returned by `GET "
                    + "/api/v1/timeline`. Not a planned trip ID.")
            @PathParam("tripId") Long tripId) {
        return tripClassificationDetailsService
                .getTripClassificationDetails(tripId, currentUserService.getCurrentUserId())
                .orElseThrow(() -> new GeoPulseException(TRIP_NOT_FOUND, "Trip not found or access denied",
                        Map.of("tripId", tripId)));
    }

}
