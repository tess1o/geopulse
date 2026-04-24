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

import java.util.List;

@Path("")
@RegisterRestClient(configKey = "nominatim-api")
public interface NominatimRestClient {
    @GET
    @Path("/reverse")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<NominatimResponse> getAddress(
            @QueryParam("format") String format,
            @QueryParam("lon") double longitude,
            @QueryParam("lat") double latitude,
            @HeaderParam("Accept-Language") String acceptLanguage);

    @GET
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<List<NominatimResponse>> search(
            @QueryParam("format") String format,
            @QueryParam("q") String query,
            @QueryParam("limit") int limit,
            @QueryParam("addressdetails") int addressDetails,
            @QueryParam("viewbox") String viewbox,
            @QueryParam("bounded") int bounded,
            @HeaderParam("Accept-Language") String acceptLanguage);
}
