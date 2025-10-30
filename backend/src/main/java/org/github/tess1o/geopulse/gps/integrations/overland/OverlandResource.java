package org.github.tess1o.geopulse.gps.integrations.overland;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandLocationMessage;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandLocations;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandResultResponse;
import org.github.tess1o.geopulse.gps.service.auth.GpsIntegrationAuthenticatorRegistry;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.Optional;
import java.util.UUID;

@Path("/")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class OverlandResource {

    private final GpsPointService gpsPointService;
    private final GpsIntegrationAuthenticatorRegistry authRegistry;

    @Inject
    public OverlandResource(GpsPointService gpsPointService, GpsIntegrationAuthenticatorRegistry authRegistry) {
        this.gpsPointService = gpsPointService;
        this.authRegistry = authRegistry;
    }

    @POST
    @Path("/api/overland")
    public Response handleOverland(OverlandLocations overlandLocations,
                                   @HeaderParam("Authorization") String overlandAuth) {
        log.info("Received payload for overland:{}", overlandLocations);

        var authResult = authRegistry.authenticate(GpsSourceType.OVERLAND, overlandAuth);
        if (authResult.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        UUID userId = authResult.get().getUserId();
        var config = authResult.get().getConfig();
        saveToDb(overlandLocations, userId, config);
        return Response.ok(new OverlandResultResponse("ok")).build();
    }

    private void saveToDb(OverlandLocations overlandLocations, UUID userId, org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity config) {
        for (OverlandLocationMessage locationMessage : overlandLocations.getLocations()) {
            gpsPointService.saveOverlandGpsPoint(locationMessage, userId, GpsSourceType.OVERLAND, config);
        }
    }
}