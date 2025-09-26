package org.github.tess1o.geopulse.ai.rest;

import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.ai.service.AIChatService;
import org.github.tess1o.geopulse.ai.service.UserAISettingsService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.time.Duration;
import java.util.UUID;

@Path("/api/ai")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
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
    @Path("/test-openai")
    public Response testOpenAIConnection(OpenAIConnectionTestRequest request) {
        try {
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .apiKey(request.apiKey())
                    .modelName(request.model())
                    .timeout(Duration.ofSeconds(10))
                    .build();
            model.chat("Hello");
            return Response.ok().build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    public record OpenAIConnectionTestRequest(String apiKey, String model) {
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

    public record ChatRequest(String message) {
    }

    public record ChatResponse(String response) {
    }
}
