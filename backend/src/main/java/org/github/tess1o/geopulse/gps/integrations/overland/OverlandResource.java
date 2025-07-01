package org.github.tess1o.geopulse.gps.integrations.overland;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandLocationMessage;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandLocations;
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
        log.info("Received payload for overland:{}, auth: {}", overlandLocations, overlandAuth);

        Optional<UUID> userIdOpt = authRegistry.authenticate(GpsSourceType.OVERLAND, overlandAuth);
        if (userIdOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        saveToDb(overlandLocations, userIdOpt.get());
        return Response.ok(new ResultResponse("ok")).build();
    }

    private void saveToDb(OverlandLocations overlandLocations, UUID userId) {
        for (OverlandLocationMessage locationMessage : overlandLocations.getLocations()) {
            gpsPointService.saveOverlandGpsPoint(locationMessage, userId, GpsSourceType.OVERLAND);
        }
    }


    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    private static class ResultResponse {
        public String result;
    }
}