package org.github.tess1o.geopulse.ai.rest;

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
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.ai.service.AIChatService;
import org.github.tess1o.geopulse.ai.service.UserAISettingsService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;
import java.util.UUID;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.AI_CONNECTION_FAILED;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/api/ai")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: AI Assistant", description = "Manage AI assistant settings and chat with the configured AI provider.")
public class AIResource {

    @Inject
    UserAISettingsService aiSettingsService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    AIChatService aiChatService;

    @Inject
    SystemSettingsService systemSettingsService;

    @GET
    @Path("/settings")
    public UserAISettings getAISettings() {
        UUID userId = currentUserService.getCurrentUserId();
        return aiSettingsService.getAISettings(userId);
    }

    @GET
    @Path("/default-system-message")
    public DefaultSystemMessageResponse getDefaultSystemMessage() {
        // Return the effective default (global setting > built-in default)
        String globalDefault = systemSettingsService.getString("ai.default-system-message");
        String effectiveDefault = (globalDefault != null && !globalDefault.isBlank())
                ? globalDefault
                : AIChatService.SYSTEM_MESSAGE;
        return new DefaultSystemMessageResponse(effectiveDefault);
    }

    @GET
    @Path("/builtin-system-message")
    public DefaultSystemMessageResponse getBuiltinSystemMessage() {
        // Return the actual built-in default (ignores global setting)
        return new DefaultSystemMessageResponse(AIChatService.SYSTEM_MESSAGE);
    }

    @POST
    @Path("/settings")
    @APIResponse(responseCode = "204", description = "AI settings saved")
    public RestResponse<Void> saveAISettings(@NotNull @Valid UserAISettings settings) {
        UUID userId = currentUserService.getCurrentUserId();
        aiSettingsService.saveAISettings(userId, settings);
        return RestResponse.noContent();
    }

    @POST
    @Path("/test-connection")
    public List<String> testConnection(@NotNull @Valid TestConnectionRequest request) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            UserAISettings settings = UserAISettings.builder()
                    .openaiApiUrl(request.openaiApiUrl())
                    .openaiApiKey(request.openaiApiKey())
                    .apiKeyRequired(request.isApiKeyNeeded())
                    .build();
            List<String> models = aiSettingsService.testConnectionAndFetchModels(userId, settings);
            return models;
        } catch (Exception e) {
            log.error("Failed to test connection", e);
            throw problem(AI_CONNECTION_FAILED,
                    e.getMessage() == null ? "Failed to connect to the AI provider" : e.getMessage());
        }
    }

    @POST
    @Path("/chat")
    public ChatResponse chat(@NotNull @Valid ChatRequest request) {
        return new ChatResponse(aiChatService.chat(request.message()));
    }

    public record TestConnectionRequest(
            @NotBlank String openaiApiUrl,
            String openaiApiKey,
            boolean isApiKeyNeeded) { }

    public record ChatRequest(@NotBlank String message) {
    }

    public record ChatResponse(String response) {
    }

    public record DefaultSystemMessageResponse(String message) {
    }
}
