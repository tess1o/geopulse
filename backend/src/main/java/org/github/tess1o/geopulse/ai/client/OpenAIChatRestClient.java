package org.github.tess1o.geopulse.ai.client;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.ai.client.dto.ChatRequest;
import org.github.tess1o.geopulse.ai.client.dto.ChatResponse;

@Path("/chat/completions")
public interface OpenAIChatRestClient {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    ChatResponse chatCompletion(ChatRequest request);
}
