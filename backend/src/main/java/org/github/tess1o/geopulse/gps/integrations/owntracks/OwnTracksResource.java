package org.github.tess1o.geopulse.gps.integrations.owntracks;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.service.auth.GpsIntegrationAuthenticatorRegistry;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.jboss.resteasy.reactive.RestHeader;

import java.time.Instant;
import java.util.*;

@Path("/")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class OwnTracksResource {

    @ConfigProperty(name = "geopulse.owntracks.ping.timestamp.override", defaultValue = "false")
    private boolean timestampOverride;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GpsPointService gpsPointService;
    private final GpsIntegrationAuthenticatorRegistry authRegistry;

    public OwnTracksResource(GpsPointService gpsPointService, GpsIntegrationAuthenticatorRegistry authRegistry) {
        this.gpsPointService = gpsPointService;
        this.authRegistry = authRegistry;
    }

    @POST
    @Path("/api/owntracks")
    public Response handleOwnTracks(Map<String, Object> payload,
                                    @HeaderParam("Authorization") String ownTrackAuth,
                                    @RestHeader("X-Limit-D") String deviceId) {
        log.info("Received payload: {}, device: {}", payload, deviceId);

        if (!"location".equals(payload.get("_type"))) {
            return Response.ok().build();
        }

        Optional<UUID> userIdOpt = authRegistry.authenticate(GpsSourceType.OWNTRACKS, ownTrackAuth);
        if (userIdOpt.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        UUID userId = userIdOpt.get();
        OwnTracksLocationMessage ownTracksLocationMessage = MAPPER.convertValue(payload, OwnTracksLocationMessage.class);

        if (timestampOverride) {
            if ("p".equals(ownTracksLocationMessage.getT())) {
                ownTracksLocationMessage.setTst(Instant.now().getEpochSecond());
            }
        }
        gpsPointService.saveOwnTracksGpsPoint(ownTracksLocationMessage, userId, deviceId, GpsSourceType.OWNTRACKS);
        return Response.ok("[]").build();
    }

}