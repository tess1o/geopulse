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
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.ai.service.AIChatService;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Path("/ai")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.AI_ASSISTANT)
public class AIChatResource {

    @Inject
    AIChatService aiChatService;

    @Inject
    SystemSettingsService systemSettingsService;

    @GET
    @Path("/system-messages/default")
    @Operation(summary = "Get the default system message",
            description = "Returns the system message used when the user has no custom one: the administrator's "
                    + "server-wide default, or the built-in message when none is set.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
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
    @Operation(summary = "Get the built-in system message",
            description = "Returns the system message that ships with GeoPulse, for example to restore it after "
                    + "editing.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public DefaultSystemMessageResponse getBuiltinSystemMessage() {
        // Return the actual built-in default (ignores global setting)
        return new DefaultSystemMessageResponse(AIChatService.SYSTEM_MESSAGE);
    }

    @POST
    @Path("/chat-completions")
    @Operation(summary = "Ask the AI assistant",
            description = "Sends a question to the AI assistant and returns its answer. The assistant uses the "
                    + "signed-in user's AI settings and can look up their timeline data, and data of friends who "
                    + "share it, to answer. The AI assistant must be enabled.")
    public ChatResponse chat(@NotNull @Valid ChatRequest request) {
        return new ChatResponse(aiChatService.chat(request.message()));
    }

    public record ChatRequest(
            @Schema(examples = "How many kilometers did I walk last week?") @NotBlank String message) {
    }

    public record ChatResponse(String response) {
    }

    public record DefaultSystemMessageResponse(String message) {
    }
}
