package org.github.tess1o.geopulse.gps.integrations.gpslogger;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.AUTHENTICATION_REQUIRED;

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
import org.github.tess1o.geopulse.shared.api.ApiPaths;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.jboss.resteasy.reactive.RestHeader;

import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.github.tess1o.geopulse.shared.openapi.ApiSecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.github.tess1o.geopulse.shared.openapi.GpsIngestExamples;

@Path(ApiPaths.GPS_INGEST + "/gpslogger")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = ApiTags.GPS_TRACKER_INGEST)
@SecurityRequirement(name = ApiSecuritySchemes.GPS_SOURCE_BASIC)
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
    @Operation(summary = "Ingest GPSLogger location",
            description = "Receives a location update from GPSLogger for Android, sent in the OwnTracks JSON format, "
                    + "and stores it as a GPS point for the user who owns the GPSLogger GPS source. Messages whose "
                    + "`_type` is not `location` are ignored.")
    @APIResponse(responseCode = "200", description = "Location accepted or ignored. The body is an empty JSON array.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(type = SchemaType.ARRAY)))
    public Response handleGpsLogger(@RequestBody(content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = OwnTracksLocationMessage.class),
                    examples = @ExampleObject(name = "location", value = GpsIngestExamples.GPSLOGGER)))
                                    Map<String, Object> payload,
                                    @HeaderParam("Authorization") String authHeader,
                                    @Parameter(description = "Device identifier stored with the GPS point. Optional.",
                                            example = "phone")
                                    @RestHeader("X-Limit-D") String deviceId) {
        long started = System.nanoTime();
        if (!"location".equals(payload.get("_type"))) {
            return Response.ok().build();
        }

        var authResult = authRegistry.authenticate(GpsSourceType.GPSLOGGER, authHeader);
        if (authResult.isEmpty()) {
            throw new GeoPulseException(AUTHENTICATION_REQUIRED, "Authentication required");
        }

        UUID userId = authResult.get().getUserId();
        var config = authResult.get().getConfig();
        OwnTracksLocationMessage locationMessage = MAPPER.convertValue(payload, OwnTracksLocationMessage.class);

        var summary = gpsPointService.saveOwnTracksGpsPoint(locationMessage, userId, deviceId, GpsSourceType.GPSLOGGER, config);
        if (summary != null) summary.logCompletion(GpsSourceType.GPSLOGGER, started);
        return Response.ok("[]").build();
    }
}
