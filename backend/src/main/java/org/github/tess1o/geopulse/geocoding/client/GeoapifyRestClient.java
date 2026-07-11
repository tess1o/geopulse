package org.github.tess1o.geopulse.geocoding.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.github.tess1o.geopulse.geocoding.model.geoapify.GeoapifyResponse;

@Path("/v1/geocode")
@RegisterRestClient(configKey = "geoapify-api")
public interface GeoapifyRestClient {

    @GET
    @Path("/reverse")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<GeoapifyResponse> reverseGeocode(
            @QueryParam("lat") double latitude,
            @QueryParam("lon") double longitude,
            @QueryParam("apiKey") String apiKey,
            @QueryParam("lang") String language);

    @GET
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<GeoapifyResponse> forwardGeocode(
            @QueryParam("text") String text,
            @QueryParam("limit") int limit,
            @QueryParam("apiKey") String apiKey,
            @QueryParam("lang") String language,
            @QueryParam("bias") String bias);
}
