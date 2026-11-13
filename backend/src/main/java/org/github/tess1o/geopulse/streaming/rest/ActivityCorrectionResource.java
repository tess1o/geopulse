package org.github.tess1o.geopulse.streaming.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.auth.model.ApiTokenScope;
import org.github.tess1o.geopulse.auth.security.RequiresApiTokenScope;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditDTO;
import org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.exception.TimelineGenerationLockException;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@Path("/timeline/activity-corrections")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = org.github.tess1o.geopulse.shared.openapi.ApiTags.TIMELINE_CORRECTIONS)
public class ActivityCorrectionResource {

    public record Response(UUID groupId, List<TimelineEditDTO> edits, UUID timelineJobId, boolean timelineJobQueued) {
    }

    @Inject
    ActivityCorrectionService service;
    @Inject
    CurrentUserService currentUser;
    @Inject
    AsyncTimelineGenerationService generation;

    @POST
    @Path("/preview")
    @RequiresApiTokenScope(ApiTokenScope.READ)
    @Operation(summary = "Review activities for an empty interval or a rebuild of an existing one",
            description = "Validates proposed manual stays and trips for empty intervals or for rebuilding an existing "
                    + "interval, and returns a timeline preview with surrounding events and remaining gaps. Does not "
                    + "save corrections or generate GPS points.")
    public ActivityCorrectionService.Preview preview(@Valid ActivityCorrectionService.Request request) {
        return checked(() -> service.preview(currentUser.getCurrentUserId(), request));
    }

    @POST
    @RequiresApiTokenScope(ApiTokenScope.WRITE)
    @Operation(summary = "Save a reversible group of manual activities without generating GPS points",
            description = "Saves manual stays and trips as one reversible correction group and schedules timeline "
                    + "regeneration. With replaceExisting=true the activities are carved out of the selected interval "
                    + "and the remaining time keeps the original event. Does not generate GPS points.")
    public Response save(@Valid ActivityCorrectionService.Request request) {
        return checked(() -> schedule(service.save(currentUser.getCurrentUserId(), request)));
    }

    @POST
    @Path("/{groupId}/revert")
    @RequiresApiTokenScope(ApiTokenScope.WRITE)
    @Operation(summary = "Undo a complete activity correction group",
            description = "Deactivates all edits in the signed-in user's correction group and schedules timeline "
                    + "regeneration from the earliest affected interval. With cascade=true, also reverts the later "
                    + "active corrections that depend on it. Keeps the group available for restoration.")
    public Response revert(@PathParam("groupId") UUID groupId,
            @QueryParam("cascade") @DefaultValue("false") boolean cascade) {
        return checked(() -> schedule(service.changeStatus(currentUser.getCurrentUserId(), groupId, false, cascade)));
    }

    @POST
    @Path("/{groupId}/restore")
    @RequiresApiTokenScope(ApiTokenScope.WRITE)
    @Operation(summary = "Restore a correction group when its intervals allow it",
            description = "Reactivates all edits in the signed-in user's correction group after checking that a "
                    + "rebuilt interval is free of newer corrections, or that an empty interval stayed empty, then "
                    + "schedules timeline regeneration. Rejects restoration if existing activities or manual "
                    + "corrections conflict.")
    public Response restore(@PathParam("groupId") UUID groupId) {
        return checked(() -> schedule(service.changeStatus(currentUser.getCurrentUserId(), groupId, true)));
    }

    private Response schedule(ActivityCorrectionService.Saved saved) {
        var result = generation.scheduleTimelineRegenerationFromTimestamp(currentUser.getCurrentUserId(), saved.affectedFrom());
        return new Response(saved.groupId(), saved.edits(), result.jobId(), result.queued());
    }

    private static <T> T checked(Supplier<T> action) {
        try {
            return action.get();
        } catch (TimelineGenerationLockException e) {
            throw new GeoPulseException(TIMELINE_REGENERATION_IN_PROGRESS, "Timeline update in progress. Try again when it finishes.", e);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, e.getMessage(), e);
        }
    }
}
