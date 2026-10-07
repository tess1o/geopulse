package org.github.tess1o.geopulse.notes.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

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
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.notes.model.MemosConfigResponse;
import org.github.tess1o.geopulse.notes.model.TestMemosConnectionRequest;
import org.github.tess1o.geopulse.notes.model.TestMemosConnectionResponse;
import org.github.tess1o.geopulse.notes.model.UpdateMemosConfigRequest;
import org.github.tess1o.geopulse.notes.service.TimelineNoteService;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.concurrent.CompletionStage;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.INVALID_MEMOS_CONFIG;

@Path("/integrations/memos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.MEMOS)
public class MemosResource {

    @Inject
    TimelineNoteService noteService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @Path("")
    @Blocking
    @APIResponse(responseCode = "204", description = "Memos is not configured")
    @Operation(summary = "Get Memos settings",
            description = "Returns the signed-in user's Memos settings: server URL, API key, whether the integration "
                    + "is enabled, the default destination and visibility for new notes, request limits, and tag "
                    + "filters. Returns `204 No Content` when Memos is not configured.")
    public RestResponse<MemosConfigResponse> getCurrentUserMemosConfig() {
        return noteService.getMemosConfig(currentUserService.getCurrentUserId())
                .map(RestResponse::ok)
                .orElseGet(RestResponse::noContent);
    }

    @PUT
    @Path("")
    @Blocking
    @APIResponse(responseCode = "204", description = "Memos configuration updated")
    @Operation(summary = "Save Memos settings",
            description = "Saves the signed-in user's Memos settings. With `includeTags` and `excludeTags` you "
                    + "choose which memos appear on the timeline.")
    public RestResponse<Void> updateCurrentUserMemosConfig(
            @NotNull @Valid UpdateMemosConfigRequest request) {
        try {
            noteService.updateMemosConfig(currentUserService.getCurrentUserId(), request);
            return RestResponse.noContent();
        } catch (IllegalArgumentException exception) {
            throw new GeoPulseException(INVALID_MEMOS_CONFIG, INVALID_MEMOS_CONFIG.title(), exception);
        }
    }

    @POST
    @Path("/connection-tests")
    @Blocking
    @Operation(summary = "Test a Memos connection",
            description = "Checks that GeoPulse can reach a Memos server with the given URL and API key, without "
                    + "saving them.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public CompletionStage<TestMemosConnectionResponse> testCurrentUserMemosConnection(
            @NotNull @Valid TestMemosConnectionRequest request) {
        return noteService.testMemosConnection(currentUserService.getCurrentUserId(), request);
    }
}
