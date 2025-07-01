package org.github.tess1o.geopulse.geocoding.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.github.tess1o.geopulse.geocoding.model.mapbox.MapboxResponse;

@Path("/geocoding/v5/mapbox.places")
@RegisterRestClient(configKey = "mapbox-api")
public interface MapboxRestClient {
    
    @GET
    @Path("/{longitude},{latitude}.json")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<MapboxResponse> reverseGeocode(
            @PathParam("longitude") double longitude,
            @PathParam("latitude") double latitude,
            @QueryParam("access_token") String accessToken,
            @QueryParam("types") String types);
}