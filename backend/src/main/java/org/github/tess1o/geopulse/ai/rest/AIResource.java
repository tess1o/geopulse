package org.github.tess1o.geopulse.ai.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.ai.service.UserAISettingsService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiPaths;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.AI_CONNECTION_FAILED;

@Path(ApiPaths.INTEGRATIONS + "/ai")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.AI_ASSISTANT)
public class AIResource {

    @Inject
    UserAISettingsService aiSettingsService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @Operation(summary = "Get AI assistant settings",
            description = "Returns the signed-in user's AI settings: whether the assistant is enabled, the "
                    + "OpenAI-compatible API URL, model, custom system message, and whether an API key is saved. "
                    + "The API key itself is not returned.")
    public UserAISettings getAISettings() {
        return aiSettingsService.getAISettings(currentUserService.getCurrentUserId());
    }

    @PUT
    @APIResponse(responseCode = "204", description = "AI settings saved")
    @Operation(summary = "Save AI assistant settings",
            description = "Saves the signed-in user's AI settings. Any OpenAI-compatible API works, including "
                    + "self-hosted servers such as Ollama; set `apiKeyRequired` to `false` for servers without "
                    + "authentication.")
    public RestResponse<Void> saveAISettings(@NotNull @Valid UserAISettings settings) {
        aiSettingsService.saveAISettings(currentUserService.getCurrentUserId(), settings);
        return RestResponse.noContent();
    }

    @POST
    @Path("/connection-tests")
    @Operation(summary = "Test an AI provider",
            description = "Checks an OpenAI-compatible API URL and key and returns the names of the models it "
                    + "offers, without saving anything.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public List<String> testConnection(@NotNull @Valid TestConnectionRequest request) {
        UserAISettings settings = UserAISettings.builder()
                .openaiApiUrl(request.openaiApiUrl())
                .openaiApiKey(request.openaiApiKey())
                .apiKeyRequired(request.isApiKeyNeeded())
                .build();
        return aiSettingsService.testConnectionAndFetchModels(
                currentUserService.getCurrentUserId(), settings);
    }

    public record TestConnectionRequest(
            @NotBlank String openaiApiUrl,
            String openaiApiKey,
            boolean isApiKeyNeeded) { }
}
