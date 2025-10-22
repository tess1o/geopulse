package org.github.tess1o.geopulse.geocoding.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.github.tess1o.geopulse.geocoding.model.photon.PhotonResponse;

@Path("/reverse")
@RegisterRestClient(configKey = "photon-api")
public interface PhotonRestClient {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Uni<PhotonResponse> getAddress(
            @QueryParam("lon") double longitude,
            @QueryParam("lat") double latitude);
}
