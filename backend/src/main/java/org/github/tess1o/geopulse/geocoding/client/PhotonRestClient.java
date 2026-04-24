package org.github.tess1o.geopulse.geocoding.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.github.tess1o.geopulse.geocoding.model.photon.PhotonResponse;

@Path("")
@RegisterRestClient(configKey = "photon-api")
public interface PhotonRestClient {
    @GET
    @Path("/reverse")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<PhotonResponse> getAddress(
            @QueryParam("lon") double longitude,
            @QueryParam("lat") double latitude,
            @HeaderParam("Accept-Language") String acceptLanguage);

    @GET
    @Path("/api")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<PhotonResponse> search(
            @QueryParam("q") String query,
            @QueryParam("limit") int limit,
            @QueryParam("lat") Double latitude,
            @QueryParam("lon") Double longitude,
            @QueryParam("zoom") Integer zoom,
            @QueryParam("lang") String language,
            @HeaderParam("Accept-Language") String acceptLanguage);
}
