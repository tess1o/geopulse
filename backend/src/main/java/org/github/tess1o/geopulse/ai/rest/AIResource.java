package org.github.tess1o.geopulse.ai.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.ai.service.AIChatService;
import org.github.tess1o.geopulse.ai.service.UserAISettingsService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.util.List;
import java.util.UUID;

@Path("/api/ai")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
public class AIResource {

    @Inject
    UserAISettingsService aiSettingsService;

    @Inject
    CurrentUserService currentUserService;
    
    @Inject
    AIChatService aiChatService;

    @GET
    @Path("/settings")
    public Response getAISettings() {
        UUID userId = currentUserService.getCurrentUserId();
        UserAISettings settings = aiSettingsService.getAISettings(userId);
        return Response.ok(settings).build();
    }

    @POST
    @Path("/settings")
    public Response saveAISettings(UserAISettings settings) {
        UUID userId = currentUserService.getCurrentUserId();
        aiSettingsService.saveAISettings(userId, settings);
        return Response.ok().build();
    }

    @POST
    @Path("/test-connection")
    public Response testConnection(TestConnectionRequest request) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            UserAISettings settings = UserAISettings.builder()
                    .openaiApiUrl(request.openaiApiUrl())
                    .openaiApiKey(request.openaiApiKey())
                    .apiKeyRequired(request.isApiKeyNeeded())
                    .build();
            List<String> models = aiSettingsService.testConnectionAndFetchModels(userId, settings);
            return Response.ok(models).build();
        } catch (Exception e) {
            log.error("Failed to test connection", e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/chat")
    public ApiResponse<?> chat(ChatRequest request) {
        try {
            String response = aiChatService.chat(request.message());
            return ApiResponse.success(new ChatResponse(response));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    public record TestConnectionRequest(String openaiApiUrl, String openaiApiKey, boolean isApiKeyNeeded) { }

    public record ChatRequest(String message) {
    }

    public record ChatResponse(String response) {
    }
}
