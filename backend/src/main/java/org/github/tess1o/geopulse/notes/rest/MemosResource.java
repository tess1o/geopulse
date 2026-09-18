package org.github.tess1o.geopulse.notes.rest;

import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.notes.model.MemosConfigResponse;
import org.github.tess1o.geopulse.notes.model.TestMemosConnectionRequest;
import org.github.tess1o.geopulse.notes.model.TestMemosConnectionResponse;
import org.github.tess1o.geopulse.notes.model.UpdateMemosConfigRequest;
import org.github.tess1o.geopulse.notes.service.TimelineNoteService;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.concurrent.CompletionStage;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_MEMOS_CONFIG;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/integrations/memos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: Memos", description = "Manage Memos configuration for live notes integration.")
public class MemosResource {

    @Inject
    TimelineNoteService noteService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @Path("")
    @Blocking
    @APIResponse(responseCode = "204", description = "Memos is not configured")
    public RestResponse<MemosConfigResponse> getCurrentUserMemosConfig() {
        return noteService.getMemosConfig(currentUserService.getCurrentUserId())
                .map(RestResponse::ok)
                .orElseGet(RestResponse::noContent);
    }

    @PUT
    @Path("")
    @Blocking
    @APIResponse(responseCode = "204", description = "Memos configuration updated")
    public RestResponse<Void> updateCurrentUserMemosConfig(
            @NotNull @Valid UpdateMemosConfigRequest request) {
        try {
            noteService.updateMemosConfig(currentUserService.getCurrentUserId(), request);
            return RestResponse.noContent();
        } catch (IllegalArgumentException exception) {
            throw problem(INVALID_MEMOS_CONFIG, exception.getMessage());
        }
    }

    @POST
    @Path("/connection-tests")
    @Blocking
    public CompletionStage<TestMemosConnectionResponse> testCurrentUserMemosConnection(
            @NotNull @Valid TestMemosConnectionRequest request) {
        return noteService.testMemosConnection(currentUserService.getCurrentUserId(), request);
    }
}
