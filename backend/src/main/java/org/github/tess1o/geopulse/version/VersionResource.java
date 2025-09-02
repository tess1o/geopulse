package org.github.tess1o.geopulse.version;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

@Path("/api/version")
public class VersionResource {
    @ConfigProperty(name = "quarkus.application.version")
    String version;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getVersion() {
        return Response.ok(Map.of("version", version)).build();
    }
}