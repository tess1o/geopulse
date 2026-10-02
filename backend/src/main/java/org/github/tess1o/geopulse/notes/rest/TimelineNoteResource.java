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

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_NOTE_RANGE;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_NOTE_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.NOTE_NOT_FOUND;

@Path("")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Notes", description = "Manage GeoPulse notes and live Memos integration.")
public class TimelineNoteResource {

    @Inject
    TimelineNoteService noteService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @Path("/notes/search")
    @Blocking
    public CompletionStage<NoteSearchResponse> searchNotes(
            @QueryParam("from") String startTime,
            @QueryParam("to") String endTime,
            @QueryParam("includeExternal") @DefaultValue("true") boolean includeExternal,
            @QueryParam("limit") Integer limit,
            @QueryParam("latitude") Double latitude,
            @QueryParam("longitude") Double longitude,
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
    public CompletionStage<NoteMapMarkersResponse> getNoteMapMarkers(
            @QueryParam("from") String startTime,
            @QueryParam("to") String endTime,
            @QueryParam("includeExternal") @DefaultValue("true") boolean includeExternal,
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
    public NoteDto updateNote(@PathParam("noteId") Long noteId,
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
    public RestResponse<Void> deleteNote(@PathParam("noteId") Long noteId) {
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
