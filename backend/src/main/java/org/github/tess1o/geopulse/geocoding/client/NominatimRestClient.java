package org.github.tess1o.geopulse.geocoding.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.github.tess1o.geopulse.geocoding.model.nominatim.NominatimResponse;

@Path("/reverse")
@RegisterRestClient(configKey = "nominatim-api")
public interface NominatimRestClient {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Uni<NominatimResponse> getAddress(
            @QueryParam("format") String format,
            @QueryParam("lon") double longitude,
            @QueryParam("lat") double latitude,
            @HeaderParam("Accept-Language") String acceptLanguage);
}