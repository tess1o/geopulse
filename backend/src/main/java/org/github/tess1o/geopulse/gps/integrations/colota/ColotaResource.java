package org.github.tess1o.geopulse.gps.integrations.colota;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.colota.model.ColotaLocationMessage;
import org.github.tess1o.geopulse.gps.service.auth.GpsIntegrationAuthenticatorRegistry;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.UUID;

@Path("/")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class ColotaResource {

    private final GpsPointService gpsPointService;
    private final GpsIntegrationAuthenticatorRegistry authRegistry;

    public ColotaResource(GpsPointService gpsPointService,
                          GpsIntegrationAuthenticatorRegistry authRegistry) {
        this.gpsPointService = gpsPointService;
        this.authRegistry = authRegistry;
    }

    @POST
    @Path("/api/colota")
    public Response handleColota(ColotaLocationMessage payload,
                                 @HeaderParam("Authorization") String authHeader) {
        log.info("Received Colota payload: {}", payload);

        var authResult = authRegistry.authenticate(GpsSourceType.COLOTA, authHeader);
        if (authResult.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        UUID userId = authResult.get().getUserId();
        var config = authResult.get().getConfig();

        gpsPointService.saveColotaGpsPoint(payload, userId, GpsSourceType.COLOTA, config);
        return Response.ok("[]").build();
    }
}
