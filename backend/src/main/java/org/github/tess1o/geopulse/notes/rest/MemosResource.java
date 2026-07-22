package org.github.tess1o.geopulse.notes.rest;

import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.notes.model.MemosConfigResponse;
import org.github.tess1o.geopulse.notes.model.TestMemosConnectionRequest;
import org.github.tess1o.geopulse.notes.model.UpdateMemosConfigRequest;
import org.github.tess1o.geopulse.notes.service.TimelineNoteService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
@Tag(name = "User: Memos", description = "Manage Memos configuration for live notes integration.")
public class MemosResource {

    @Inject
    TimelineNoteService noteService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @Path("/me/memos-config")
    @RolesAllowed({"USER", "ADMIN"})
    @Blocking
    public Response getCurrentUserMemosConfig() {
        UUID userId = currentUserService.getCurrentUserId();
        Optional<MemosConfigResponse> config = noteService.getMemosConfig(userId);
        return Response.ok(ApiResponse.success(config.orElse(null))).build();
    }

    @PUT
    @Path("/me/memos-config")
    @RolesAllowed({"USER", "ADMIN"})
    @Blocking
    public Response updateCurrentUserMemosConfig(@Valid UpdateMemosConfigRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        try {
            noteService.updateMemosConfig(userId, request);
            return Response.ok(ApiResponse.success("Memos configuration updated successfully")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to update Memos config for user {}: {}", userId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to update Memos configuration"))
                    .build();
        }
    }

    @POST
    @Path("/me/memos-config/test")
    @RolesAllowed({"USER", "ADMIN"})
    @Blocking
    public CompletableFuture<Response> testCurrentUserMemosConnection(@Valid TestMemosConnectionRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        return noteService.testMemosConnection(userId, request)
                .thenApply(result -> Response.ok(ApiResponse.success(result)).build())
                .exceptionally(throwable -> {
                    log.error("Failed to test Memos connection for user {}: {}", userId, throwable.getMessage(), throwable);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity(ApiResponse.error("Failed to test connection"))
                            .build();
                });
    }
}
