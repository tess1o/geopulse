package org.github.tess1o.geopulse.sharing.rest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.sharing.model.*;
import org.github.tess1o.geopulse.sharing.service.SharedLinkService;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Path("/api/shared")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
public class PublicSharedLinkResource {

    @Inject
    SharedLinkService sharedLinkService;

    @GET
    @Path("/{linkId}/info")
    public Response getSharedLocationInfo(@PathParam("linkId") UUID linkId) {
        try {
            SharedLocationInfo result = sharedLinkService.getSharedLocationInfo(linkId);
            return Response.ok(result).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Link not found or expired"))
                    .build();
        } catch (Exception e) {
            log.error("Error getting shared location info for linkId: {}", linkId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve link information"))
                    .build();
        }
    }

    @POST
    @Path("/{linkId}/verify")
    public Response verifyPassword(@PathParam("linkId") UUID linkId, @Valid VerifyPasswordRequest request) {
        try {
            AccessTokenResponse result = sharedLinkService.verifyPassword(linkId, request.getPassword());
            return Response.ok(result).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Link not found or expired"))
                    .build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Invalid password"))
                    .build();
        } catch (Exception e) {
            log.error("Error verifying password for linkId: {}", linkId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Verification failed"))
                    .build();
        }
    }

    @GET
    @Path("/{linkId}/location")
    public Response getSharedLocation(@PathParam("linkId") UUID linkId, @HeaderParam("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ApiResponse.error("Authorization token required"))
                        .build();
            }

            String token = authHeader.substring("Bearer ".length());
            LocationHistoryResponse result = sharedLinkService.getSharedLocation(linkId, token);
            return Response.ok(result).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Link not found or expired"))
                    .build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Access denied"))
                    .build();
        } catch (Exception e) {
            log.error("Error getting shared location for linkId: {}", linkId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve location"))
                    .build();
        }
    }

    @GET
    @Path("/{linkId}/timeline")
    public Response getSharedTimeline(
            @PathParam("linkId") UUID linkId,
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ApiResponse.error("Authorization token required"))
                        .build();
            }

            String token = authHeader.substring("Bearer ".length());

            // Parse optional date parameters
            Instant startInstant = null;
            Instant endInstant = null;

            if (startTime != null && !startTime.isEmpty()) {
                try {
                    startInstant = Instant.parse(startTime);
                } catch (Exception e) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(ApiResponse.error("Invalid startTime format. Expected ISO-8601"))
                            .build();
                }
            }

            if (endTime != null && !endTime.isEmpty()) {
                try {
                    endInstant = Instant.parse(endTime);
                } catch (Exception e) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(ApiResponse.error("Invalid endTime format. Expected ISO-8601"))
                            .build();
                }
            }

            // Validate start < end if both provided
            if (startInstant != null && endInstant != null && startInstant.isAfter(endInstant)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("startTime must be before endTime"))
                        .build();
            }

            MovementTimelineDTO result = sharedLinkService.getSharedTimeline(linkId, token, startInstant, endInstant);
            return Response.ok(result).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Link not found or expired"))
                    .build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Access denied"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Error getting shared timeline for linkId: {}", linkId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve timeline"))
                    .build();
        }
    }

    @GET
    @Path("/{linkId}/path")
    public Response getSharedPath(
            @PathParam("linkId") UUID linkId,
            @HeaderParam("Authorization") String authHeader,
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ApiResponse.error("Authorization token required"))
                        .build();
            }

            String token = authHeader.substring("Bearer ".length());

            // Parse optional date parameters
            Instant startInstant = null;
            Instant endInstant = null;

            if (startTime != null && !startTime.isEmpty()) {
                try {
                    startInstant = Instant.parse(startTime);
                } catch (Exception e) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(ApiResponse.error("Invalid startTime format. Expected ISO-8601"))
                            .build();
                }
            }

            if (endTime != null && !endTime.isEmpty()) {
                try {
                    endInstant = Instant.parse(endTime);
                } catch (Exception e) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(ApiResponse.error("Invalid endTime format. Expected ISO-8601"))
                            .build();
                }
            }

            // Validate start < end if both provided
            if (startInstant != null && endInstant != null && startInstant.isAfter(endInstant)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error("startTime must be before endTime"))
                        .build();
            }

            GpsPointPathDTO result = sharedLinkService.getSharedPath(linkId, token, startInstant, endInstant);
            return Response.ok(result).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Link not found or expired"))
                    .build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Access denied"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Error getting shared path for linkId: {}", linkId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve path"))
                    .build();
        }
    }

    @GET
    @Path("/{linkId}/current")
    public Response getSharedCurrentLocation(@PathParam("linkId") UUID linkId, @HeaderParam("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ApiResponse.error("Authorization token required"))
                        .build();
            }

            String token = authHeader.substring("Bearer ".length());
            Optional<LocationHistoryResponse.CurrentLocationData> result =
                    sharedLinkService.getSharedCurrentLocation(linkId, token);

            if (result.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("Current location not available"))
                        .build();
            }

            return Response.ok(result.get()).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("Current location not available"))
                    .build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Access denied"))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Error getting current location for linkId: {}", linkId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve current location"))
                    .build();
        }
    }
}