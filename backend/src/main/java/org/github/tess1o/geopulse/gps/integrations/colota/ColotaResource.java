package org.github.tess1o.geopulse.gps.integrations.colota;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.AUTHENTICATION_REQUIRED;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.colota.model.ColotaLocationMessage;
import org.github.tess1o.geopulse.gps.service.auth.GpsIntegrationAuthenticatorRegistry;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.shared.api.ApiPaths;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

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
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.github.tess1o.geopulse.shared.openapi.GpsIngestExamples;

@Path(ApiPaths.GPS_INGEST + "/colota")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = ApiTags.GPS_TRACKER_INGEST)
@SecurityRequirement(name = ApiSecuritySchemes.GPS_SOURCE_BASIC)
public class ColotaResource {

    private final GpsPointService gpsPointService;
    private final GpsIntegrationAuthenticatorRegistry authRegistry;

    public ColotaResource(GpsPointService gpsPointService,
                          GpsIntegrationAuthenticatorRegistry authRegistry) {
        this.gpsPointService = gpsPointService;
        this.authRegistry = authRegistry;
    }

    @POST
    @Operation(summary = "Ingest Colota location",
            description = "Receives a location update from the Colota app and stores it as a GPS point for the user "
                    + "who owns the Colota GPS source.")
    @APIResponse(responseCode = "200", description = "Location accepted. The body is an empty JSON array.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(type = SchemaType.ARRAY)))
    public Response handleColota(@RequestBody(content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(name = "location", value = GpsIngestExamples.COLOTA)))
                                 ColotaLocationMessage payload,
                                 @HeaderParam("Authorization") String authHeader) {
        long started = System.nanoTime();
        var authResult = authRegistry.authenticate(GpsSourceType.COLOTA, authHeader);
        if (authResult.isEmpty()) {
            throw new GeoPulseException(AUTHENTICATION_REQUIRED, "Authentication required");
        }

        UUID userId = authResult.get().getUserId();
        var config = authResult.get().getConfig();

        var summary = gpsPointService.saveColotaGpsPoint(payload, userId, GpsSourceType.COLOTA, config);
        if (summary != null) summary.logCompletion(GpsSourceType.COLOTA, started);
        return Response.ok("[]").build();
    }
}
