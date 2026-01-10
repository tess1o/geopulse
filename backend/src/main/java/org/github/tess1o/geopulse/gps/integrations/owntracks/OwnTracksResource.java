package org.github.tess1o.geopulse.gps.integrations.owntracks;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.integrations.owntracks.service.OwnTracksPoiService;
import org.github.tess1o.geopulse.gps.integrations.owntracks.service.OwnTracksTagService;
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
    private final OwnTracksPoiService ownTracksPoiService;
    private final OwnTracksTagService ownTracksTagService;

    public OwnTracksResource(GpsPointService gpsPointService,
                           GpsIntegrationAuthenticatorRegistry authRegistry,
                           OwnTracksPoiService ownTracksPoiService,
                           OwnTracksTagService ownTracksTagService) {
        this.gpsPointService = gpsPointService;
        this.authRegistry = authRegistry;
        this.ownTracksPoiService = ownTracksPoiService;
        this.ownTracksTagService = ownTracksTagService;
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

        var authResult = authRegistry.authenticate(GpsSourceType.OWNTRACKS, ownTrackAuth);
        if (authResult.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        UUID userId = authResult.get().getUserId();
        var config = authResult.get().getConfig();
        OwnTracksLocationMessage ownTracksLocationMessage = MAPPER.convertValue(payload, OwnTracksLocationMessage.class);

        if (timestampOverride) {
            if ("p".equals(ownTracksLocationMessage.getT())) {
                ownTracksLocationMessage.setTst(Instant.now().getEpochSecond());
            }
        }

        // Handle POI if present
        if (ownTracksLocationMessage.getPoi() != null && !ownTracksLocationMessage.getPoi().trim().isEmpty()) {
            try {
                ownTracksPoiService.handlePoi(ownTracksLocationMessage, userId);
            } catch (Exception e) {
                log.error("Failed to handle OwnTracks POI: {}", e.getMessage(), e);
                // Continue processing GPS point even if POI handling fails
            }
        }

        // Handle tag if present
        if (ownTracksLocationMessage.getTag() != null) {
            try {
                ownTracksTagService.handleTag(ownTracksLocationMessage, userId);
            } catch (Exception e) {
                log.error("Failed to handle OwnTracks tag: {}", e.getMessage(), e);
                // Continue processing GPS point even if tag handling fails
            }
        }

        gpsPointService.saveOwnTracksGpsPoint(ownTracksLocationMessage, userId, deviceId, GpsSourceType.OWNTRACKS, config);
        return Response.ok("[]").build();
    }

}