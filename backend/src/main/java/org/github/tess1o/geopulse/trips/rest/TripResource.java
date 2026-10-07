package org.github.tess1o.geopulse.trips.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.trips.model.dto.CreateTripDto;
import org.github.tess1o.geopulse.trips.model.dto.TripCollaboratorDto;
import org.github.tess1o.geopulse.trips.model.dto.TripDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripCollaboratorDto;
import org.github.tess1o.geopulse.trips.model.dto.UpdateTripDto;
import org.github.tess1o.geopulse.trips.service.TripService;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TRIP_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TIMELINE_LABEL_NOT_FOUND;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TRIP_NOT_FOUND;

@Path("/trips")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.TRIPS)
public class TripResource {

    private final TripService tripService;
    private final CurrentUserService currentUserService;

    @Inject
    public TripResource(TripService tripService, CurrentUserService currentUserService) {
        this.tripService = tripService;
        this.currentUserService = currentUserService;
    }

    @GET
    @Operation(summary = "List trips",
            description = "Returns the trips the signed-in user owns and the trips shared with them as a "
                    + "collaborator.")
    public List<TripDto> getTrips(
            @Parameter(description = "Only trips with this status: `UNPLANNED`, `UPCOMING`, `ACTIVE`, `COMPLETED`, "
                    + "or `CANCELLED`.", example = "UPCOMING")
            @QueryParam("status") String status) {
        try {
            return tripService.getTrips(currentUserService.getCurrentUserId(), status);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a trip",
            description = "Returns a trip with its name, dates, status, color, and the timeline label it is linked "
                    + "to, if any.")
    public TripDto getTrip(
            @Parameter(description = "Trip ID.")
            @PathParam("id") Long id) {
        try {
            return tripService.getTrip(currentUserService.getCurrentUserId(), id);
        } catch (NotFoundException e) {
            throw notFound(id, e);
        }
    }

    @POST
    @Operation(summary = "Create a trip",
            description = "Creates a trip for a date range. The status follows the dates (upcoming, active, "
                    + "completed) unless set otherwise.")
    public RestResponse<TripDto> createTrip(@Valid CreateTripDto dto) {
        try {
            return RestResponse.status(Response.Status.CREATED,
                    tripService.createTrip(currentUserService.getCurrentUserId(), dto));
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    @POST
    @Path("/from-timeline-label/{timelineLabelId}")
    @Operation(summary = "Create a trip from a timeline label",
            description = "Creates a trip with the name, dates, and color of an existing timeline label and links "
                    + "the two. Later changes to the label update the trip.")
    public RestResponse<TripDto> createTripFromTimelineLabel(
            @Parameter(description = "Timeline label ID.")
            @PathParam("timelineLabelId") Long timelineLabelId) {
        try {
            return RestResponse.status(Response.Status.CREATED,
                    tripService.createTripFromTimelineLabel(currentUserService.getCurrentUserId(), timelineLabelId));
        } catch (NotFoundException e) {
            throw new GeoPulseException(TIMELINE_LABEL_NOT_FOUND, "Timeline label not found", Map.of("timelineLabelId", timelineLabelId), e);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a trip",
            description = "Changes the name, dates, status, color, or notes of a trip.")
    public TripDto updateTrip(
            @Parameter(description = "Trip ID.")
            @PathParam("id") Long id, @Valid UpdateTripDto dto) {
        try {
            return tripService.updateTrip(currentUserService.getCurrentUserId(), id, dto);
        } catch (NotFoundException e) {
            throw notFound(id, e);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a trip",
            description = "Deletes a trip. If the trip is linked to a timeline label, `mode` decides what happens to "
                    + "the label.")
    public RestResponse<Void> deleteTrip(
            @Parameter(description = "Trip ID.")
            @PathParam("id") Long id,
            @Parameter(description = "`unlink_only` (default) keeps the linked timeline label; `delete_both` deletes "
                    + "it too.")
            @QueryParam("mode") @DefaultValue("unlink_only") String mode) {
        if (!"unlink_only".equalsIgnoreCase(mode) && !"delete_both".equalsIgnoreCase(mode)) {
            throw new GeoPulseException(INVALID_TRIP_REQUEST, "Invalid delete mode",
                    Map.of("mode", mode, "allowedValues", "unlink_only,delete_both"));
        }
        try {
            tripService.deleteTrip(currentUserService.getCurrentUserId(), id, "delete_both".equalsIgnoreCase(mode));
            return RestResponse.noContent();
        } catch (NotFoundException e) {
            throw notFound(id, e);
        }
    }

    @DELETE
    @Path("/{id}/timeline-label")
    @Operation(summary = "Unlink a trip from its timeline label",
            description = "Removes the link between a trip and its timeline label. Both are kept; changes to one no "
                    + "longer affect the other.")
    public TripDto unlinkTripFromTimelineLabel(
            @Parameter(description = "Trip ID.")
            @PathParam("id") Long id) {
        try {
            return tripService.unlinkTripFromTimelineLabel(currentUserService.getCurrentUserId(), id);
        } catch (NotFoundException e) {
            throw notFound(id, e);
        }
    }

    @GET
    @Path("/{id}/collaborators")
    @Operation(summary = "List trip collaborators",
            description = "Returns the friends a trip is shared with and their access level (`VIEW` or `EDIT`).")
    public List<TripCollaboratorDto> getTripCollaborators(
            @Parameter(description = "Trip ID.")
            @PathParam("id") Long id) {
        try {
            return tripService.getTripCollaborators(currentUserService.getCurrentUserId(), id);
        } catch (NotFoundException e) {
            throw notFound(id, e);
        }
    }

    @PUT
    @Path("/{id}/collaborators/{friendId}")
    @Operation(summary = "Share a trip with a friend",
            description = "Adds a friend as a collaborator on a trip, or changes their access level: `VIEW` lets "
                    + "them see the trip, `EDIT` also lets them change the plan.")
    public TripCollaboratorDto upsertTripCollaborator(
            @Parameter(description = "Trip ID.")
            @PathParam("id") Long id,
            @Parameter(description = "User ID of the friend.")
            @PathParam("friendId") String friendId,
            @Valid UpdateTripCollaboratorDto dto) {
        try {
            return tripService.upsertTripCollaborator(
                    currentUserService.getCurrentUserId(), id, UUID.fromString(friendId), dto);
        } catch (NotFoundException e) {
            throw notFound(id, e);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    @DELETE
    @Path("/{id}/collaborators/{friendId}")
    @Operation(summary = "Stop sharing a trip with a friend",
            description = "Removes a collaborator from a trip.")
    public RestResponse<Void> removeTripCollaborator(
            @Parameter(description = "Trip ID.")
            @PathParam("id") Long id,
            @Parameter(description = "User ID of the friend.")
            @PathParam("friendId") String friendId) {
        try {
            tripService.removeTripCollaborator(
                    currentUserService.getCurrentUserId(), id, UUID.fromString(friendId));
            return RestResponse.noContent();
        } catch (NotFoundException e) {
            throw notFound(id, e);
        } catch (IllegalArgumentException e) {
            throw invalid(e);
        }
    }

    private static GeoPulseException notFound(Long tripId, NotFoundException cause) {
        return new GeoPulseException(TRIP_NOT_FOUND, "Trip not found", Map.of("tripId", tripId), cause);
    }

    private static GeoPulseException invalid(IllegalArgumentException exception) {
        return new GeoPulseException(INVALID_TRIP_REQUEST, "Invalid trip request", exception);
    }
}
