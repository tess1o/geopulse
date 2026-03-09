package org.github.tess1o.geopulse.gps.integrations.traccar;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.traccar.model.TraccarPositionData;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gps.service.auth.GpsIntegrationAuthenticatorRegistry;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.UUID;

@Path("/")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class TraccarResource {

    private final GpsPointService gpsPointService;
    private final GpsIntegrationAuthenticatorRegistry authRegistry;

    public TraccarResource(GpsPointService gpsPointService,
                           GpsIntegrationAuthenticatorRegistry authRegistry) {
        this.gpsPointService = gpsPointService;
        this.authRegistry = authRegistry;
    }

    @POST
    @Path("/api/traccar")
    public Response handleTraccar(TraccarPositionData payload,
                                  @HeaderParam("Authorization") String authHeader) {
        log.info("Received Traccar payload: {}", payload);


        var authResult = authRegistry.authenticate(GpsSourceType.TRACCAR, authHeader);
        if (authResult.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        UUID userId = authResult.get().getUserId();
        var config = authResult.get().getConfig();
        gpsPointService.saveTraccarGpsPoint(payload, userId, GpsSourceType.TRACCAR, config);
        return Response.ok().build();
    }
}
