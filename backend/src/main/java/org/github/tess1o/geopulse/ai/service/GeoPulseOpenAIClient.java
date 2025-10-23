package org.github.tess1o.geopulse.ai.service;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@Path("/")
@RegisterRestClient(configKey = "openai")
public interface GeoPulseOpenAIClient {

    @GET
    @Path("/models")
    @Produces(MediaType.APPLICATION_JSON)
    ModelsResponse getModels();

    // Response DTOs
    record ModelsResponse(
            String object,
            List<Model> data
    ) {}

    record Model(
            String id,
            String object,
            Long created,
            String owned_by
    ) {}
}