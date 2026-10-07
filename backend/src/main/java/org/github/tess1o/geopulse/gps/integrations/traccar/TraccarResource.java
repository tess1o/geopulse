package org.github.tess1o.geopulse.gps.integrations.traccar;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.AUTHENTICATION_REQUIRED;

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
import org.github.tess1o.geopulse.shared.api.ApiPaths;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.List;
import java.util.Locale;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.github.tess1o.geopulse.shared.openapi.ApiSecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.github.tess1o.geopulse.shared.openapi.GpsIngestExamples;

@Path(ApiPaths.GPS_INGEST + "/traccar")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = ApiTags.GPS_TRACKER_INGEST)
@SecurityRequirement(name = ApiSecuritySchemes.GPS_SOURCE_TOKEN)
public class TraccarResource {

    private final GpsPointService gpsPointService;
    private final GpsSourceService gpsSourceService;

    public TraccarResource(GpsPointService gpsPointService,
                           GpsSourceService gpsSourceService) {
        this.gpsPointService = gpsPointService;
        this.gpsSourceService = gpsSourceService;
    }

    @POST
    @Operation(summary = "Ingest Traccar position",
            description = "Receives a position forwarded by a Traccar server and stores it as a GPS point. Several "
                    + "Traccar GPS sources can share one token: the position is stored for every active source whose "
                    + "device ID matches the position's device or, when none matches, for every source without a "
                    + "device ID.")
    @APIResponse(responseCode = "200", description = "Position accepted, or ignored when no source matches the device")
    public Response handleTraccar(@RequestBody(content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(name = "position", value = GpsIngestExamples.TRACCAR)))
                                  TraccarPositionData payload,
                                  @HeaderParam("Authorization") String authHeader) {
        long started = System.nanoTime();
        String token;
        try {
            token = extractBearerToken(authHeader);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(AUTHENTICATION_REQUIRED, "Authentication required", e);
        }

        List<GpsSourceConfigEntity> tokenConfigs = gpsSourceService.findAllActiveByTokenAndSourceType(token, GpsSourceType.TRACCAR);
        if (tokenConfigs.isEmpty()) {
            throw new GeoPulseException(AUTHENTICATION_REQUIRED, "Authentication required");
        }

        List<GpsSourceConfigEntity> matchedConfigs = resolveMatchedConfigs(payload, tokenConfigs);
        if (matchedConfigs.isEmpty()) {
            log.debug("No eligible Traccar config route for incoming device id");
            return Response.ok().build();
        }

        var summary = new GpsPointService.GpsIngestSummary(0, 0, 0, 0);
        for (GpsSourceConfigEntity config : matchedConfigs) {
            summary = summary.plus(gpsPointService.saveTraccarGpsPoint(payload, config.getUser().getId(), GpsSourceType.TRACCAR, config));
        }
        summary.logCompletion(GpsSourceType.TRACCAR, started);
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
