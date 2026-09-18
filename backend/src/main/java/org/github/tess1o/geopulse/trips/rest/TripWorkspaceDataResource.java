package org.github.tess1o.geopulse.trips.rest;

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

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TRIP_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/trips/{tripId}")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Trips", description = "Read trip workspace timeline and path data.")
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
    public MovementTimelineDTO getTripTimeline(@PathParam("tripId") Long tripId,
                                    @QueryParam("from") String startTime,
                                    @QueryParam("to") String endTime) {
        try {
            Instant parsedStart = parseInstant(startTime);
            Instant parsedEnd = parseInstant(endTime);
            return tripWorkspaceDataService.getTripTimeline(
                    currentUserService.getCurrentUserId(), tripId, parsedStart, parsedEnd);
        } catch (NotFoundException e) {
            throw problem(TRIP_NOT_FOUND, "Trip not found", Map.of("tripId", tripId));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw problem(INVALID_TRIP_REQUEST, detail(e));
        }
    }

    @GET
    @Path("/path")
    public GpsPointPathDTO getTripPath(@PathParam("tripId") Long tripId,
                                @QueryParam("from") String startTime,
                                @QueryParam("to") String endTime) {
        try {
            Instant parsedStart = parseInstant(startTime);
            Instant parsedEnd = parseInstant(endTime);
            return tripWorkspaceDataService.getTripPath(
                    currentUserService.getCurrentUserId(), tripId, parsedStart, parsedEnd);
        } catch (NotFoundException e) {
            throw problem(TRIP_NOT_FOUND, "Trip not found", Map.of("tripId", tripId));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw problem(INVALID_TRIP_REQUEST, detail(e));
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    private static String detail(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Invalid trip workspace range"
                : exception.getMessage();
    }
}
