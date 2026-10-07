package org.github.tess1o.geopulse.notes.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.notes.model.CreateNoteRequest;
import org.github.tess1o.geopulse.notes.model.NoteDto;
import org.github.tess1o.geopulse.notes.model.NoteMapMarkersResponse;
import org.github.tess1o.geopulse.notes.model.NoteSearchResponse;
import org.github.tess1o.geopulse.notes.model.UpdateNoteRequest;
import org.github.tess1o.geopulse.notes.service.TimelineNoteService;
import org.jboss.resteasy.reactive.RestResponse;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_NOTE_RANGE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_NOTE_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.NOTE_NOT_FOUND;

@Path("")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.NOTES)
public class TimelineNoteResource {

    @Inject
    TimelineNoteService noteService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @Path("/notes/search")
    @Blocking
    @Operation(summary = "Search notes",
            description = "Returns notes in a time range, ordered by event time: notes stored in GeoPulse and, "
                    + "unless disabled, notes from the connected Memos server. Optionally only notes near a "
                    + "location.")
    public CompletionStage<NoteSearchResponse> searchNotes(
            @Parameter(description = "Start of the time range, as an ISO-8601 instant. Defaults to the earliest data.",
                    example = "2025-06-01T00:00:00Z")
            @QueryParam("from") String startTime,
            @Parameter(description = "End of the time range, as an ISO-8601 instant. Defaults to now.",
                    example = "2025-06-30T23:59:59Z")
            @QueryParam("to") String endTime,
            @Parameter(description = "Also include notes from the connected Memos server. Defaults to `true`.")
            @QueryParam("includeExternal") @DefaultValue("true") boolean includeExternal,
            @Parameter(description = "Maximum number of notes, up to 5000. Defaults to 1000.")
            @QueryParam("limit") Integer limit,
            @Parameter(description = "Latitude of the search center. Use together with `longitude` and "
                    + "`radiusMeters`.")
            @QueryParam("latitude") Double latitude,
            @Parameter(description = "Longitude of the search center.")
            @QueryParam("longitude") Double longitude,
            @Parameter(description = "Search radius around the center, in meters.")
            @QueryParam("radiusMeters") Double radiusMeters) {
        Instant start = parseInstantOrDefault(startTime, Instant.EPOCH);
        Instant end = parseInstantOrDefault(endTime, Instant.now());
        validateRange(start, end);
        return noteService.searchNotes(currentUserService.getCurrentUserId(), start, end,
                includeExternal, limit, latitude, longitude, radiusMeters);
    }

    @GET
    @Path("/notes/map-markers")
    @Blocking
    @Operation(summary = "Get note map markers",
            description = "Returns notes in a time range grouped into map markers. Notes whose coordinates match "
                    + "after rounding to `coordinatePrecision` decimal places share one marker.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public CompletionStage<NoteMapMarkersResponse> getNoteMapMarkers(
            @Parameter(description = "Start of the time range, as an ISO-8601 instant. Defaults to the earliest data.",
                    example = "2025-06-01T00:00:00Z")
            @QueryParam("from") String startTime,
            @Parameter(description = "End of the time range, as an ISO-8601 instant. Defaults to now.",
                    example = "2025-06-30T23:59:59Z")
            @QueryParam("to") String endTime,
            @Parameter(description = "Also include notes from the connected Memos server. Defaults to `true`.")
            @QueryParam("includeExternal") @DefaultValue("true") boolean includeExternal,
            @Parameter(description = "Decimal places used to group nearby notes, from 3 to 6. Defaults to 4 (about "
                    + "10 m).")
            @QueryParam("coordinatePrecision") Integer coordinatePrecision) {
        Instant start = parseInstantOrDefault(startTime, Instant.EPOCH);
        Instant end = parseInstantOrDefault(endTime, Instant.now());
        validateRange(start, end);
        return noteService.getMapMarkers(currentUserService.getCurrentUserId(), start, end,
                includeExternal, coordinatePrecision);
    }

    @POST
    @Path("/notes")
    @Blocking
    @APIResponse(responseCode = "201", description = "Note created")
    @Operation(summary = "Create a note",
            description = "Creates a note attached to a moment, a stay, or a trip (`anchorType`: `TIMESTAMP`, "
                    + "`STAY`, or `TRIP`), with optional coordinates. Set `destination` to `GEOPULSE` to store it "
                    + "in GeoPulse or `MEMOS` to create it on the connected Memos server.")
    public CompletionStage<RestResponse<NoteDto>> createNote(@NotNull @Valid CreateNoteRequest request) {
        try {
            return noteService.createNote(currentUserService.getCurrentUserId(), request)
                    .thenApply(note -> RestResponse.status(Response.Status.CREATED, note));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new GeoPulseException(INVALID_NOTE_REQUEST, INVALID_NOTE_REQUEST.title(), exception);
        }
    }

    @PATCH
    @Path("/notes/{noteId}")
    @Blocking
    @Operation(summary = "Update a note",
            description = "Updates a note stored in GeoPulse. Notes stored in Memos are edited in Memos.")
    public NoteDto updateNote(
            @Parameter(description = "Note ID.")
            @PathParam("noteId") Long noteId,
            @NotNull @Valid UpdateNoteRequest request) {
        try {
            return noteService.updateLocalNote(currentUserService.getCurrentUserId(), noteId, request);
        } catch (NoSuchElementException exception) {
            throw new GeoPulseException(NOTE_NOT_FOUND, NOTE_NOT_FOUND.title(), exception);
        }
    }

    @DELETE
    @Path("/notes/{noteId}")
    @Blocking
    @APIResponse(responseCode = "204", description = "Note deleted")
    @Operation(summary = "Delete a note",
            description = "Deletes a note stored in GeoPulse.")
    public RestResponse<Void> deleteNote(
            @Parameter(description = "Note ID.")
            @PathParam("noteId") Long noteId) {
        try {
            noteService.deleteLocalNote(currentUserService.getCurrentUserId(), noteId);
            return RestResponse.noContent();
        } catch (NoSuchElementException exception) {
            throw new GeoPulseException(NOTE_NOT_FOUND, NOTE_NOT_FOUND.title(), exception);
        }
    }

    private Instant parseInstantOrDefault(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new GeoPulseException(INVALID_NOTE_RANGE, "Use ISO-8601 time format", exception);
        }
    }

    private void validateRange(Instant startTime, Instant endTime) {
        if (startTime.isAfter(endTime)) {
            throw new GeoPulseException(INVALID_NOTE_RANGE, "Start time must be before end time");
        }
    }
}
