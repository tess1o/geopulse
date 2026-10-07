package org.github.tess1o.geopulse.trips.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.trips.service.TripWorkspaceDataService;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TRIP_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_NOT_FOUND;

@Path("/trips/{tripId}")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.TRIPS)
public class TripWorkspaceDataResource {

    private final TripWorkspaceDataService tripWorkspaceDataService;
    private final CurrentUserService currentUserService;

    public TripWorkspaceDataResource(TripWorkspaceDataService tripWorkspaceDataService,
                                     CurrentUserService currentUserService) {
        this.tripWorkspaceDataService = tripWorkspaceDataService;
        this.currentUserService = currentUserService;
    }

    @GET
    @Path("/timeline")
    @Operation(summary = "Get the timeline of a trip",
            description = "Returns the stays, trips, and data gaps of the trip owner during the trip. Collaborators "
                    + "see the owner's timeline. Optionally narrow the range within the trip dates.")
    public MovementTimelineDTO getTripTimeline(
            @Parameter(description = "Trip ID.")
            @PathParam("tripId") Long tripId,
            @Parameter(description = "Start of the range, as an ISO-8601 instant. Defaults to the trip start; must "
                    + "be within the trip.")
            @QueryParam("from") String startTime,
            @Parameter(description = "End of the range, as an ISO-8601 instant. Defaults to the trip end; must be "
                    + "within the trip.")
            @QueryParam("to") String endTime) {
        try {
            Instant parsedStart = parseInstant(startTime);
            Instant parsedEnd = parseInstant(endTime);
            return tripWorkspaceDataService.getTripTimeline(
                    currentUserService.getCurrentUserId(), tripId, parsedStart, parsedEnd);
        } catch (NotFoundException e) {
            throw new GeoPulseException(TRIP_NOT_FOUND, "Trip not found", Map.of("tripId", tripId), e);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new GeoPulseException(INVALID_TRIP_REQUEST, "Invalid trip workspace range", e);
        }
    }

    @GET
    @Path("/path")
    @Operation(summary = "Get the GPS path of a trip",
            description = "Returns the trip owner's GPS path during the trip for drawing on a map. Optionally narrow "
                    + "the range within the trip dates.")
    public GpsPointPathDTO getTripPath(
            @Parameter(description = "Trip ID.")
            @PathParam("tripId") Long tripId,
            @Parameter(description = "Start of the range, as an ISO-8601 instant. Defaults to the trip start; must "
                    + "be within the trip.")
            @QueryParam("from") String startTime,
            @Parameter(description = "End of the range, as an ISO-8601 instant. Defaults to the trip end; must be "
                    + "within the trip.")
            @QueryParam("to") String endTime) {
        try {
            Instant parsedStart = parseInstant(startTime);
            Instant parsedEnd = parseInstant(endTime);
            return tripWorkspaceDataService.getTripPath(
                    currentUserService.getCurrentUserId(), tripId, parsedStart, parsedEnd);
        } catch (NotFoundException e) {
            throw new GeoPulseException(TRIP_NOT_FOUND, "Trip not found", Map.of("tripId", tripId), e);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new GeoPulseException(INVALID_TRIP_REQUEST, "Invalid trip workspace range", e);
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

}
