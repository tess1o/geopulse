package org.github.tess1o.geopulse.gps.integrations.gpslogger;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gps.service.auth.GpsIntegrationAuthenticatorRegistry;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.jboss.resteasy.reactive.RestHeader;

import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = "User: GPS Integrations", description = "Ingest location updates from GPS Logger clients.")
public class GpsLoggerResource {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GpsPointService gpsPointService;
    private final GpsIntegrationAuthenticatorRegistry authRegistry;

    public GpsLoggerResource(GpsPointService gpsPointService,
                             GpsIntegrationAuthenticatorRegistry authRegistry) {
        this.gpsPointService = gpsPointService;
        this.authRegistry = authRegistry;
    }

    @POST
    @Path("/api/gpslogger")
    @Operation(summary = "Ingest GPS Logger location",
            description = "Receives a GPS Logger location update and stores it as a GPS point for the matching source token.")
    public Response handleGpsLogger(Map<String, Object> payload,
                                    @HeaderParam("Authorization") String authHeader,
                                    @RestHeader("X-Limit-D") String deviceId) {
        log.info("Received GPSLogger payload: {}, device: {}", payload, deviceId);

        if (!"location".equals(payload.get("_type"))) {
            return Response.ok().build();
        }

        var authResult = authRegistry.authenticate(GpsSourceType.GPSLOGGER, authHeader);
        if (authResult.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        UUID userId = authResult.get().getUserId();
        var config = authResult.get().getConfig();
        OwnTracksLocationMessage locationMessage = MAPPER.convertValue(payload, OwnTracksLocationMessage.class);

        gpsPointService.saveOwnTracksGpsPoint(locationMessage, userId, deviceId, GpsSourceType.GPSLOGGER, config);
        return Response.ok("[]").build();
    }
}
