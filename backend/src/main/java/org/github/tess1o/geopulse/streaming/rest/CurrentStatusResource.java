package org.github.tess1o.geopulse.streaming.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.streaming.model.dto.CurrentStatusDTO;
import org.github.tess1o.geopulse.streaming.service.CurrentActivityService;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Instant;

/**
 * What the signed-in user is doing now. The class path is empty (as in {@code UserResource}) so it does not shadow
 * {@code GET /users/me}.
 */
@Path("")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@RequestScoped
@Tag(name = ApiTags.TIMELINE)
public class CurrentStatusResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    CurrentActivityService currentActivityService;

    @GET
    @Path("/users/me/status")
    @Operation(summary = "Get current status",
            description = "Returns what the signed-in user is doing now (`STAY`, `TRIP`, `DATA_GAP`, `UNKNOWN` when "
                    + "the timeline has not been generated yet, or `NO_DATA`), with the place or trip, the latest GPS "
                    + "point, and totals for today in the user's timezone. Durations are counted to now. The timeline "
                    + "itself is updated every few minutes, so a new stay or trip can appear with that delay. Meant "
                    + "for dashboards and home automation polling every minute or so; a client that wants "
                    + "\"stay started\" or \"trip ended\" events compares successive responses.")
    public CurrentStatusDTO getStatus() {
        UserEntity user = currentUserService.getCurrentUser();
        return currentActivityService.getStatus(user.getId(), user.getTimezone(), Instant.now());
    }
}
