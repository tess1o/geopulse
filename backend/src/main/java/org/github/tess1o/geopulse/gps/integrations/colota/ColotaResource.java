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

@Path(ApiPaths.GPS_INGEST + "/colota")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = "User: GPS Integrations", description = "Ingest location updates from Colota-compatible clients.")
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
            description = "Receives a Colota-compatible location update and stores it for the matching source token.")
    @APIResponse(responseCode = "200", description = "Location accepted",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(type = SchemaType.ARRAY)))
    public Response handleColota(ColotaLocationMessage payload,
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
