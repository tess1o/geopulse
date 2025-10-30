package org.github.tess1o.geopulse.gps.integrations.dawarich.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.point.DawarichPayload;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.stats.DawarichMonthlyDistanceKm;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.stats.DawarichStatsResponse;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.stats.DawarichYearlyStats;
import org.github.tess1o.geopulse.gps.service.GpsPointService;
import org.github.tess1o.geopulse.gps.service.auth.GpsIntegrationAuthenticatorRegistry;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Path("/api/dawarich/api/v1")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class DawarichResource {

    private final GpsPointService gpsPointService;
    private final GpsIntegrationAuthenticatorRegistry authRegistry;


    public DawarichResource(GpsPointService gpsPointService, GpsIntegrationAuthenticatorRegistry authRegistry) {
        this.gpsPointService = gpsPointService;
        this.authRegistry = authRegistry;
    }

    @GET
    @Path("/health")
    public Response handleDawarichHealth(Request request, @HeaderParam("Authorization") String authHeader) {
        log.info("Received health request");
        var authenticated = authRegistry.authenticate(GpsSourceType.DAWARICH, authHeader);
        String dawarichResponse = authenticated.isPresent() ? "Hey, I'm alive and authenticated!" : "Hey, I'm alive!";
        return Response
                .status(Response.Status.OK)
                .header("X-Dawarich-Response", dawarichResponse)
                .header("X-Dawarich-Version", "0.30.8")
                .entity("{\"status\": \"ok\"}")
                .build();
    }

    @POST
    @Path("/points")
    public Response handleDawarichGet(DawarichPayload payload, @HeaderParam("Authorization") String authHeader) {
        var authResult = authRegistry.authenticate(GpsSourceType.DAWARICH, authHeader);
        if (authResult.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        log.info("Received payload: {}", payload);
        UUID userId = authResult.get().getUserId();
        var config = authResult.get().getConfig();
        gpsPointService.saveDarawichGpsPoints(payload, userId, GpsSourceType.DAWARICH, config);
        return Response.ok().build();
    }

    @GET
    @Path("/stats")
    public Response handleDawarichStats(@QueryParam("api_key") String apiKey) {
        log.info("Received stats request with api_key: {}", apiKey);


        var authResult = authRegistry.authenticate(GpsSourceType.DAWARICH, apiKey);
        if (authResult.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        //TODO: implement proper stats, probably use stats service
        DawarichStatsResponse statsResponse = DawarichStatsResponse.builder()
                .totalDistanceKm(1000)
                .totalCitiesVisited(2)
                .totalReverseGeocodedPoints(200)
                .totalCountriesVisited(1)
                .yearlyStats(List.of(
                        DawarichYearlyStats.builder()
                                .year(2025)
                                .totalCitiesVisited(1)
                                .totalCountriesVisited(2)
                                .totalDistanceKm(100)
                                .monthlyDistanceKm(DawarichMonthlyDistanceKm.builder()
                                        .april(200)
                                        .june(100)
                                        .build())
                                .build()
                ))
                .build();

        return Response
                .status(Response.Status.OK)
                .header("Content-Type", "application/json")
                .entity(statsResponse)
                .build();
    }
}
