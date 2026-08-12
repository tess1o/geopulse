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
import org.github.tess1o.geopulse.gps.integrations.owntracks.service.OwnTracksPayloadDecryptionService;
import org.github.tess1o.geopulse.gps.integrations.owntracks.service.OwnTracksTagService;
import org.github.tess1o.geopulse.gps.service.auth.GpsIntegrationAuthenticatorRegistry;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.jboss.resteasy.reactive.RestHeader;

import java.time.Instant;
import java.util.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/owntracks")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = "User: GPS Integrations", description = "Ingest location updates from OwnTracks clients.")
public class OwnTracksResource {

    @ConfigProperty(name = "geopulse.owntracks.ping.timestamp.override", defaultValue = "false")
    private boolean timestampOverride;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EMPTY_JSON_ARRAY = "[]";

    private final GpsPointService gpsPointService;
    private final GpsIntegrationAuthenticatorRegistry authRegistry;
    private final OwnTracksPoiService ownTracksPoiService;
    private final OwnTracksTagService ownTracksTagService;
    private final OwnTracksPayloadDecryptionService payloadDecryptionService;

    public OwnTracksResource(GpsPointService gpsPointService,
                           GpsIntegrationAuthenticatorRegistry authRegistry,
                           OwnTracksPoiService ownTracksPoiService,
                           OwnTracksTagService ownTracksTagService,
                           OwnTracksPayloadDecryptionService payloadDecryptionService) {
        this.gpsPointService = gpsPointService;
        this.authRegistry = authRegistry;
        this.ownTracksPoiService = ownTracksPoiService;
        this.ownTracksTagService = ownTracksTagService;
        this.payloadDecryptionService = payloadDecryptionService;
    }

    @POST
    @Operation(summary = "Ingest OwnTracks location",
            description = "Receives an OwnTracks location update and stores it as a GPS point for the matching source token.")
    public Response handleOwnTracks(Map<String, Object> payload,
                                    @HeaderParam("Authorization") String ownTrackAuth,
                                    @RestHeader("X-Limit-D") String deviceId) {
        log.info("Received OwnTracks HTTP payload type: {}, device: {}", payload.get("_type"), deviceId);

        if (!"location".equals(payload.get("_type")) && !payloadDecryptionService.isEncryptedPayload(payload)) {
            return Response.ok(EMPTY_JSON_ARRAY).build();
        }

        var authResult = authRegistry.authenticate(GpsSourceType.OWNTRACKS, ownTrackAuth);
        if (authResult.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        UUID userId = authResult.get().getUserId();
        var config = authResult.get().getConfig();
        Optional<Map<String, Object>> resolvedPayload = payloadDecryptionService.decryptIfNeeded(payload, config);
        if (resolvedPayload.isEmpty()) {
            return Response.ok(EMPTY_JSON_ARRAY).build();
        }

        Map<String, Object> locationPayload = resolvedPayload.get();
        if (!"location".equals(locationPayload.get("_type"))) {
            return Response.ok(EMPTY_JSON_ARRAY).build();
        }

        OwnTracksLocationMessage ownTracksLocationMessage = MAPPER.convertValue(locationPayload, OwnTracksLocationMessage.class);
        String resolvedDeviceId = resolveDeviceId(deviceId, ownTracksLocationMessage.getTopic());

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

        // Handle tag (including null/empty to end active tags)
        try {
            ownTracksTagService.handleTag(ownTracksLocationMessage, userId);
        } catch (Exception e) {
            log.error("Failed to handle OwnTracks tag: {}", e.getMessage(), e);
            // Continue processing GPS point even if tag handling fails
        }

        gpsPointService.saveOwnTracksGpsPoint(ownTracksLocationMessage, userId, resolvedDeviceId, GpsSourceType.OWNTRACKS, config);
        return Response.ok(EMPTY_JSON_ARRAY).build();
    }

    private String resolveDeviceId(String headerDeviceId, String payloadTopic) {
        if (headerDeviceId != null && !headerDeviceId.isBlank()) {
            return headerDeviceId;
        }
        if (payloadTopic == null || payloadTopic.isBlank()) {
            return headerDeviceId;
        }

        String[] topicParts = payloadTopic.split("/");
        if (topicParts.length == 3 && "owntracks".equals(topicParts[0])) {
            return topicParts[2];
        }
        return headerDeviceId;
    }

}
