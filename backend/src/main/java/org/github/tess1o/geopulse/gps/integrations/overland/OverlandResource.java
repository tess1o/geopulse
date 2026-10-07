package org.github.tess1o.geopulse.gps.integrations.overland;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.AUTHENTICATION_REQUIRED;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandLocationMessage;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandLocations;
import org.github.tess1o.geopulse.gps.integrations.overland.model.OverlandResultResponse;
import org.github.tess1o.geopulse.gps.service.auth.GpsIntegrationAuthenticatorRegistry;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.shared.api.ApiPaths;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.github.tess1o.geopulse.shared.openapi.ApiSecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.github.tess1o.geopulse.shared.openapi.GpsIngestExamples;

@Path(ApiPaths.GPS_INGEST + "/overland")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = ApiTags.GPS_TRACKER_INGEST)
@SecurityRequirement(name = ApiSecuritySchemes.GPS_SOURCE_TOKEN)
public class OverlandResource {

    private final GpsPointService gpsPointService;
    private final GpsIntegrationAuthenticatorRegistry authRegistry;

    @Inject
    public OverlandResource(GpsPointService gpsPointService, GpsIntegrationAuthenticatorRegistry authRegistry) {
        this.gpsPointService = gpsPointService;
        this.authRegistry = authRegistry;
    }

    @POST
    @Operation(summary = "Ingest Overland locations",
            description = "Receives a batch of locations from the Overland app and stores them as GPS points for "
                    + "the user who owns the Overland GPS source. Overland removes the batch from the device once it "
                    + "receives `{\"result\": \"ok\"}`.")
    @APIResponseSchema(value = OverlandResultResponse.class, responseCode = "200",
            responseDescription = "Batch accepted")
    public Response handleOverland(@RequestBody(content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(name = "batch", value = GpsIngestExamples.OVERLAND)))
                                   OverlandLocations overlandLocations,
                                   @HeaderParam("Authorization") String overlandAuth) {
        long started = System.nanoTime();
        var authResult = authRegistry.authenticate(GpsSourceType.OVERLAND, overlandAuth);
        if (authResult.isEmpty()) {
            throw new GeoPulseException(AUTHENTICATION_REQUIRED, "Authentication required");
        }

        UUID userId = authResult.get().getUserId();
        var config = authResult.get().getConfig();
        saveToDb(overlandLocations, userId, config).logCompletion(GpsSourceType.OVERLAND, started);
        return Response.ok(new OverlandResultResponse("ok")).build();
    }

    private GpsPointService.GpsIngestSummary saveToDb(OverlandLocations overlandLocations, UUID userId, org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity config) {
        var summary = new GpsPointService.GpsIngestSummary(0, 0, 0, 0);
        for (OverlandLocationMessage locationMessage : overlandLocations.getLocations()) {
            summary = summary.plus(gpsPointService.saveOverlandGpsPoint(locationMessage, userId, GpsSourceType.OVERLAND, config));
        }
        return summary;
    }
}
