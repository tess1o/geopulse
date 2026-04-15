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
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.service.GpsSourceService;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.List;
import java.util.Locale;

@Path("/")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class TraccarResource {

    private final GpsPointService gpsPointService;
    private final GpsSourceService gpsSourceService;

    public TraccarResource(GpsPointService gpsPointService,
                           GpsSourceService gpsSourceService) {
        this.gpsPointService = gpsPointService;
        this.gpsSourceService = gpsSourceService;
    }

    @POST
    @Path("/api/traccar")
    public Response handleTraccar(TraccarPositionData payload,
                                  @HeaderParam("Authorization") String authHeader) {
        log.info("Received Traccar payload: {}", payload);

        String token;
        try {
            token = extractBearerToken(authHeader);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<GpsSourceConfigEntity> tokenConfigs = gpsSourceService.findAllActiveByTokenAndSourceType(token, GpsSourceType.TRACCAR);
        if (tokenConfigs.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        List<GpsSourceConfigEntity> matchedConfigs = resolveMatchedConfigs(payload, tokenConfigs);
        if (matchedConfigs.isEmpty()) {
            log.info("No eligible Traccar config route for token and incoming device id");
            return Response.ok().build();
        }

        for (GpsSourceConfigEntity config : matchedConfigs) {
            gpsPointService.saveTraccarGpsPoint(payload, config.getUser().getId(), GpsSourceType.TRACCAR, config);
        }
        return Response.ok().build();
    }

    private List<GpsSourceConfigEntity> resolveMatchedConfigs(TraccarPositionData payload, List<GpsSourceConfigEntity> tokenConfigs) {
        String incomingDeviceId = normalizeDeviceId(payload != null && payload.getDevice() != null
                ? payload.getDevice().getUniqueId()
                : null);

        List<GpsSourceConfigEntity> exactMatches = incomingDeviceId == null
                ? List.of()
                : tokenConfigs.stream()
                        .filter(config -> incomingDeviceId.equals(normalizeDeviceId(config.getDeviceId())))
                        .toList();

        if (!exactMatches.isEmpty()) {
            return exactMatches;
        }

        // Wildcard fallback: use configs without a device filter only when no exact match exists.
        return tokenConfigs.stream()
                .filter(config -> normalizeDeviceId(config.getDeviceId()) == null)
                .toList();
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid Bearer Auth header");
        }
        return authHeader.substring("Bearer ".length());
    }

    private String normalizeDeviceId(String deviceId) {
        if (deviceId == null) {
            return null;
        }
        String trimmed = deviceId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
