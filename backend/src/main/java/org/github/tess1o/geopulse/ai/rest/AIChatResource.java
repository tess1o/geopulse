package org.github.tess1o.geopulse.ai.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.ai.service.AIChatService;

@Path("/ai")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: AI Assistant", description = "Manage AI assistant settings and chat with the configured AI provider.")
public class AIChatResource {

    @Inject
    AIChatService aiChatService;

    @Inject
    SystemSettingsService systemSettingsService;

    @GET
    @Path("/system-messages/default")
    public DefaultSystemMessageResponse getDefaultSystemMessage() {
        // Return the effective default (global setting > built-in default)
        String globalDefault = systemSettingsService.getString("ai.default-system-message");
        String effectiveDefault = (globalDefault != null && !globalDefault.isBlank())
                ? globalDefault
                : AIChatService.SYSTEM_MESSAGE;
        return new DefaultSystemMessageResponse(effectiveDefault);
    }

    @GET
    @Path("/system-messages/builtin")
    public DefaultSystemMessageResponse getBuiltinSystemMessage() {
        // Return the actual built-in default (ignores global setting)
        return new DefaultSystemMessageResponse(AIChatService.SYSTEM_MESSAGE);
    }

    @POST
    @Path("/chat-completions")
    public ChatResponse chat(@NotNull @Valid ChatRequest request) {
        return new ChatResponse(aiChatService.chat(request.message()));
    }

    public record ChatRequest(@NotBlank String message) {
    }

    public record ChatResponse(String response) {
    }

    public record DefaultSystemMessageResponse(String message) {
    }
}
