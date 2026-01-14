package org.github.tess1o.geopulse.ai;

import io.quarkus.runtime.annotations.RegisterForProxy;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.ai.model.*;
import org.github.tess1o.geopulse.ai.rest.AIResource;
import org.github.tess1o.geopulse.ai.service.AIChatService;
import org.github.tess1o.geopulse.ai.service.AITimelineTools;
import org.github.tess1o.geopulse.ai.service.GeoPulseOpenAIClient;
import org.github.tess1o.geopulse.ai.service.SimpleAITools;

@RegisterForProxy(
        targets = {
                AIChatService.Assistant.class,
        }
)
@RegisterForReflection(
        targets = {
                AITimelineTools.class,
                SimpleAITools.class,
                StayGroupBy.class,
                TripGroupBy.class,
                UserAISettings.class,
                AIMovementTimelineDTO.class,
                AIStayStatsDTO.class,
                AITimelineStayDTO.class,
                AITimelineTripDTO.class,
                AITripStatsDTO.class,
                AIResource.ChatRequest.class,
                AIResource.ChatResponse.class,
                AIResource.DefaultSystemMessageResponse.class,
                AIChatService.class,
                AIResource.TestConnectionRequest.class,
                GeoPulseOpenAIClient.class,
        }
)
public class AINativeConfig {
}
