package org.github.tess1o.geopulse.digest.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.digest.model.TimeDigest;
import org.github.tess1o.geopulse.digest.service.DigestService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.util.UUID;

@Path("/api/digest")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Slf4j
public class DigestResource {

    @Inject
    DigestService digestService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @Path("/monthly")
    public Response getMonthlyDigest(@QueryParam("year") int year, @QueryParam("month") int month) {
        var user = currentUserService.getCurrentUser();
        UUID userId = user.getId();
        String timezone = user.getTimezone();

        // Validate parameters
        if (year < 2000 || year > 2100) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid year. Must be between 2000 and 2100"))
                    .build();
        }

        if (month < 1 || month > 12) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid month. Must be between 1 and 12"))
                    .build();
        }

        log.info("Received request for monthly digest: user={}, year={}, month={}", userId, year, month);

        try {
            TimeDigest digest = digestService.getMonthlyDigest(userId, year, month, timezone);
            return Response.ok(ApiResponse.success(digest)).build();

        } catch (Exception e) {
            log.error("Failed to generate monthly digest for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to generate digest: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/yearly")
    public Response getYearlyDigest(@QueryParam("year") int year) {
        var user = currentUserService.getCurrentUser();
        UUID userId = user.getId();
        String timezone = user.getTimezone();

        // Validate parameters
        if (year < 2000 || year > 2100) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Invalid year. Must be between 2000 and 2100"))
                    .build();
        }

        log.info("Received request for yearly digest: user={}, year={}", userId, year);

        try {
            TimeDigest digest = digestService.getYearlyDigest(userId, year, timezone);
            return Response.ok(ApiResponse.success(digest)).build();

        } catch (Exception e) {
            log.error("Failed to generate yearly digest for user {}", userId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to generate digest: " + e.getMessage()))
                    .build();
        }
    }
}
