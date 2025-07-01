package org.github.tess1o.geopulse.geocoding.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.github.tess1o.geopulse.geocoding.model.googlemaps.GoogleMapsResponse;

@Path("/geocode")
@RegisterRestClient(configKey = "googlemaps-api")
public interface GoogleMapsRestClient {
    
    @GET
    @Path("/json")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<GoogleMapsResponse> reverseGeocode(
            @QueryParam("latlng") String latlng,
            @QueryParam("key") String apiKey,
            @QueryParam("result_type") String resultType);
}