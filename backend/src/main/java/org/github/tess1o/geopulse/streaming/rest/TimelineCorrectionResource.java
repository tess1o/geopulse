package org.github.tess1o.geopulse.streaming.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.model.ApiTokenScope;
import org.github.tess1o.geopulse.auth.security.RequiresApiTokenScope;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.streaming.edits.ActivityCorrectionService;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditService;
import org.github.tess1o.geopulse.streaming.exception.TimelineGenerationLockException;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineCorrectionRequest;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.service.AsyncTimelineGenerationService;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_TIMELINE_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.TIMELINE_REGENERATION_IN_PROGRESS;

/**
 * The correction API: one resource for filling time, editing events, removing events, replacing corrections and
 * undoing them. A request is either an activity batch ({@code activities}) or a single event correction ({@code op});
 * both are saved as one correction group.
 */
@Path("/timeline/corrections")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = org.github.tess1o.geopulse.shared.openapi.ApiTags.TIMELINE_CORRECTIONS)
public class TimelineCorrectionResource {

    public record Response(UUID groupId, List<TimelineEditDTO> edits, UUID timelineJobId,
                           boolean timelineJobQueued) {
    }

    @Inject
    ActivityCorrectionService corrections;
    @Inject
    TimelineEditService edits;
    @Inject
    CurrentUserService currentUser;
    @Inject
    AsyncTimelineGenerationService generation;

    @GET
    @RequiresApiTokenScope(ApiTokenScope.READ)
    @Operation(summary = "List timeline corrections",
            description = "Every correction of the signed-in user, oldest first. Optionally restricted to "
                    + "corrections whose affected range intersects [from, to).")
    public List<TimelineEditDTO> list(@QueryParam("from") String from, @QueryParam("to") String to) {
        Instant fromInstant = parseInstant(from);
        Instant toInstant = parseInstant(to);
        var all = edits.list(currentUser.getCurrentUserId());
        if (fromInstant == null && toInstant == null) {
            return all;
        }
        return all.stream()
                .filter(edit -> (toInstant == null || edit.affectedFrom().isBefore(toInstant))
                        && (fromInstant == null || edit.affectedTo().isAfter(fromInstant)))
                .toList();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, "Invalid timestamp: " + value, e);
        }
    }

    @POST
    @Path("/preview")
    @RequiresApiTokenScope(ApiTokenScope.READ)
    @Operation(summary = "Review a correction batch",
            description = "Runs the exact fold used by save and returns the resulting events, warnings and a preview "
                    + "token. Warnings describe carved events and corrections; INVALID warnings block save.")
    public ActivityCorrectionService.Preview preview(@Valid TimelineCorrectionRequest request) {
        return checked(() -> request.activityBatch()
                ? corrections.preview(currentUser.getCurrentUserId(), request.toActivityRequest())
                : previewCorrection(request));
    }

    private ActivityCorrectionService.Preview previewCorrection(TimelineCorrectionRequest request) {
        TimelineEditRequest edit = request.toEditRequest();
        var result = edits.preview(currentUser.getCurrentUserId(), edit);
        return new ActivityCorrectionService.Preview(result.events(), edit.getAffectedFrom(), edit.getAffectedTo(),
                edits.fillWarnings(currentUser.getCurrentUserId(), List.of(edit), request.getReplacesGroupId()), result.token());
    }

    @POST
    @RequiresApiTokenScope(ApiTokenScope.WRITE)
    @Operation(summary = "Save a correction batch",
            description = "Saves one or more corrections as an atomic correction group, optionally replacing an "
                    + "earlier group, and schedules timeline regeneration. UI saves supply the preview token; API "
                    + "clients may omit it.")
    public Response save(@Valid TimelineCorrectionRequest request) {
        return checked(() -> request.activityBatch()
                ? schedule(corrections.save(currentUser.getCurrentUserId(), request.toActivityRequest()))
                : schedule(edits.saveGrouped(currentUser.getCurrentUserId(), request.toEditRequest())));
    }

    @POST
    @Path("/{groupId}/undo")
    @RequiresApiTokenScope(ApiTokenScope.WRITE)
    @Operation(summary = "Undo a correction group",
            description = "Removes the group from the fold and schedules regeneration. With cascade=true, also undoes "
                    + "the later corrections that depend on it.")
    public Response undo(@PathParam("groupId") UUID groupId,
                         @QueryParam("cascade") @DefaultValue("false") boolean cascade) {
        return checked(() -> schedule(corrections.changeStatus(currentUser.getCurrentUserId(), groupId, false, cascade)));
    }

    @POST
    @Path("/{groupId}/restore")
    @RequiresApiTokenScope(ApiTokenScope.WRITE)
    @Operation(summary = "Restore a correction group",
            description = "Puts an undone group back into the fold and schedules regeneration.")
    public Response restore(@PathParam("groupId") UUID groupId) {
        return checked(() -> schedule(corrections.changeStatus(currentUser.getCurrentUserId(), groupId, true)));
    }

    private Response schedule(ActivityCorrectionService.Saved saved) {
        var result = generation.scheduleTimelineRegenerationFromTimestamp(
                currentUser.getCurrentUserId(), saved.affectedFrom());
        return new Response(saved.groupId(), saved.edits(), result.jobId(), result.queued());
    }

    private static <T> T checked(Supplier<T> action) {
        try {
            return action.get();
        } catch (TimelineGenerationLockException e) {
            throw new GeoPulseException(TIMELINE_REGENERATION_IN_PROGRESS,
                    "Timeline update in progress. Try again when it finishes.", e);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(INVALID_TIMELINE_REQUEST, e.getMessage(), e);
        }
    }
}
