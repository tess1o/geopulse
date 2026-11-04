package org.github.tess1o.geopulse.streaming.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
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
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@Path("/timeline/activity-corrections")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@org.eclipse.microprofile.openapi.annotations.tags.Tag(name = org.github.tess1o.geopulse.shared.openapi.ApiTags.TIMELINE_CORRECTIONS)
public class ActivityCorrectionResource {
    public record Response(UUID groupId, List<TimelineEditDTO> edits, UUID timelineJobId, boolean timelineJobQueued) {}
    @Inject ActivityCorrectionService service;
    @Inject CurrentUserService currentUser;
    @Inject AsyncTimelineGenerationService generation;

    @POST @Path("/preview") @RequiresApiTokenScope(ApiTokenScope.READ)
    @org.eclipse.microprofile.openapi.annotations.Operation(summary = "Review activities proposed for empty timeline intervals")
    public ActivityCorrectionService.Preview preview(@Valid ActivityCorrectionService.Request request) {
        return checked(() -> service.preview(currentUser.getCurrentUserId(), request));
    }
    @POST @RequiresApiTokenScope(ApiTokenScope.WRITE)
    @org.eclipse.microprofile.openapi.annotations.Operation(summary = "Save a reversible group of manual activities without generating GPS points")
    public Response save(@Valid ActivityCorrectionService.Request request) {
        return checked(() -> schedule(service.save(currentUser.getCurrentUserId(), request)));
    }
    @POST @Path("/{groupId}/revert") @RequiresApiTokenScope(ApiTokenScope.WRITE)
    @org.eclipse.microprofile.openapi.annotations.Operation(summary = "Undo a complete activity correction group")
    public Response revert(@PathParam("groupId") UUID groupId) {
        return checked(() -> schedule(service.changeStatus(currentUser.getCurrentUserId(), groupId, false)));
    }
    @POST @Path("/{groupId}/restore") @RequiresApiTokenScope(ApiTokenScope.WRITE)
    @org.eclipse.microprofile.openapi.annotations.Operation(summary = "Restore a correction group when its intervals remain empty")
    public Response restore(@PathParam("groupId") UUID groupId) {
        return checked(() -> schedule(service.changeStatus(currentUser.getCurrentUserId(), groupId, true)));
    }
    private Response schedule(ActivityCorrectionService.Saved saved) {
        var result = generation.scheduleTimelineRegenerationFromTimestamp(currentUser.getCurrentUserId(), saved.affectedFrom());
        return new Response(saved.groupId(), saved.edits(), result.jobId(), result.queued());
    }
    private static <T> T checked(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (TimelineGenerationLockException e) { throw new GeoPulseException(TIMELINE_REGENERATION_IN_PROGRESS, "Timeline update in progress. Try again when it finishes.", e); }
        catch (IllegalArgumentException e) { throw new GeoPulseException(INVALID_TIMELINE_REQUEST, e.getMessage(), e); }
    }
}
