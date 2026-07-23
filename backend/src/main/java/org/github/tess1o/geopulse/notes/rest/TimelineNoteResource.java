package org.github.tess1o.geopulse.notes.rest;

import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.notes.model.*;
import org.github.tess1o.geopulse.notes.service.TimelineNoteService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
@Tag(name = "User: Notes", description = "Manage GeoPulse notes and live Memos integration.")
public class TimelineNoteResource {

    @Inject
    TimelineNoteService noteService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @Path("/notes/search")
    @RolesAllowed({"USER", "ADMIN"})
    @Blocking
    public CompletableFuture<Response> searchNotes(
            @QueryParam("startTime") String startTimeStr,
            @QueryParam("endTime") String endTimeStr,
            @QueryParam("includeExternal") @DefaultValue("true") boolean includeExternal,
            @QueryParam("limit") Integer limit,
            @QueryParam("latitude") Double latitude,
            @QueryParam("longitude") Double longitude,
            @QueryParam("radiusMeters") Double radiusMeters
    ) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            Instant startTime = parseInstantOrDefault(startTimeStr, Instant.EPOCH);
            Instant endTime = parseInstantOrDefault(endTimeStr, Instant.now());
            validateRange(startTime, endTime);

            return noteService.searchNotes(userId, startTime, endTime, includeExternal, limit, latitude, longitude, radiusMeters)
                    .thenApply(result -> Response.ok(ApiResponse.success(result)).build())
                    .exceptionally(throwable -> {
                        log.error("Failed to search notes for user {}: {}", userId, throwable.getMessage(), throwable);
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                .entity(ApiResponse.error("Failed to search notes"))
                                .build();
                    });
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(toBadRequest("Invalid note search parameters: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to search notes for user {}: {}", userId, e.getMessage(), e);
            return CompletableFuture.completedFuture(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to search notes"))
                    .build());
        }
    }

    @GET
    @Path("/notes/map-markers")
    @RolesAllowed({"USER", "ADMIN"})
    @Blocking
    public CompletableFuture<Response> getNoteMapMarkers(
            @QueryParam("startTime") String startTimeStr,
            @QueryParam("endTime") String endTimeStr,
            @QueryParam("includeExternal") @DefaultValue("true") boolean includeExternal,
            @QueryParam("coordinatePrecision") Integer coordinatePrecision
    ) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            Instant startTime = parseInstantOrDefault(startTimeStr, Instant.EPOCH);
            Instant endTime = parseInstantOrDefault(endTimeStr, Instant.now());
            validateRange(startTime, endTime);

            return noteService.getMapMarkers(userId, startTime, endTime, includeExternal, coordinatePrecision)
                    .thenApply(result -> Response.ok(ApiResponse.success(result)).build())
                    .exceptionally(throwable -> {
                        log.error("Failed to load note map markers for user {}: {}", userId, throwable.getMessage(), throwable);
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                .entity(ApiResponse.error("Failed to load note map markers"))
                                .build();
                    });
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(toBadRequest("Invalid note map parameters: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to load note map markers for user {}: {}", userId, e.getMessage(), e);
            return CompletableFuture.completedFuture(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to load note map markers"))
                    .build());
        }
    }

    @POST
    @Path("/notes")
    @RolesAllowed({"USER", "ADMIN"})
    @Blocking
    public CompletableFuture<Response> createNote(@Valid CreateNoteRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            return noteService.createNote(userId, request)
                    .thenApply(note -> Response.ok(ApiResponse.success(note)).build())
                    .exceptionally(throwable -> {
                        log.error("Failed to create note for user {}: {}", userId, throwable.getMessage(), throwable);
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                .entity(ApiResponse.error("Failed to create note"))
                                .build();
                    });
        } catch (IllegalArgumentException | IllegalStateException e) {
            return CompletableFuture.completedFuture(toBadRequest(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create note for user {}: {}", userId, e.getMessage(), e);
            return CompletableFuture.completedFuture(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to create note"))
                    .build());
        }
    }

    @PATCH
    @Path("/notes/{noteId}")
    @RolesAllowed({"USER", "ADMIN"})
    @Blocking
    public Response updateNote(@PathParam("noteId") Long noteId, @Valid UpdateNoteRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            NoteDto note = noteService.updateLocalNote(userId, noteId, request);
            return Response.ok(ApiResponse.success(note)).build();
        } catch (NoSuchElementException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to update note {} for user {}: {}", noteId, userId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to update note"))
                    .build();
        }
    }

    @DELETE
    @Path("/notes/{noteId}")
    @RolesAllowed({"USER", "ADMIN"})
    @Blocking
    public Response deleteNote(@PathParam("noteId") Long noteId) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            noteService.deleteLocalNote(userId, noteId);
            return Response.ok(ApiResponse.success("Note deleted")).build();
        } catch (NoSuchElementException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to delete note {} for user {}: {}", noteId, userId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to delete note"))
                    .build();
        }
    }

    private Instant parseInstantOrDefault(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Use ISO-8601 time format");
        }
    }

    private void validateRange(Instant startTime, Instant endTime) {
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
    }

    private Response toBadRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiResponse.error(message))
                .build();
    }
}
