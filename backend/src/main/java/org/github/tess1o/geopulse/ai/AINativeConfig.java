package org.github.tess1o.geopulse.ai;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.ai.client.dto.*;
import org.github.tess1o.geopulse.ai.model.*;
import org.github.tess1o.geopulse.ai.rest.AIResource;
import org.github.tess1o.geopulse.ai.service.AIChatService;
import org.github.tess1o.geopulse.ai.service.GeoPulseOpenAIClient;

@RegisterForReflection(
        targets = {
                // Existing AI model classes
                StayGroupBy.class,
                TripGroupBy.class,
                UserAISettings.class,
                AIMovementTimelineDTO.class,
                AIStayStatsDTO.class,
                AITimelineStayDTO.class,
                AITimelineTripDTO.class,
                AITripStatsDTO.class,

                // REST API DTOs
                AIResource.ChatRequest.class,
                AIResource.ChatResponse.class,
                AIResource.DefaultSystemMessageResponse.class,
                AIResource.TestConnectionRequest.class,

                // Services
                AIChatService.class,
                GeoPulseOpenAIClient.class,

                // OpenAI Client DTOs (new)
                org.github.tess1o.geopulse.ai.client.dto.ChatRequest.class,
                org.github.tess1o.geopulse.ai.client.dto.ChatResponse.class,
                ChatMessage.class,
                ToolCall.class,
                FunctionCall.class,
                FunctionDefinition.class,
                FunctionSpec.class,
                Choice.class,
                Usage.class,
        }
)
public class AINativeConfig {
}
