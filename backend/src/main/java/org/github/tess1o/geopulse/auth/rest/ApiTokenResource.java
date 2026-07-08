package org.github.tess1o.geopulse.auth.rest;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.auth.dto.CreateApiTokenRequest;
import org.github.tess1o.geopulse.auth.dto.UpdateApiTokenRequest;
import org.github.tess1o.geopulse.auth.service.ApiTokenService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;

import java.util.UUID;

@Path("/api/api-tokens")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
public class ApiTokenResource {

    @Context
    HttpServerRequest request;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ApiTokenService apiTokenService;

    @GET
    public Response listTokens() {
        UUID userId = currentUserService.getCurrentUserId();
        return Response.ok(ApiResponse.success(apiTokenService.listForUser(userId))).build();
    }

    @POST
    public Response createToken(
            @Valid CreateApiTokenRequest createRequest,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            String ipAddress = UserIpAddress.resolve(request, forwardedFor, realIp);
            return Response.status(Response.Status.CREATED)
                    .entity(ApiResponse.success(apiTokenService.createToken(
                            userId,
                            createRequest.getName(),
                            createRequest.getExpiresAt(),
                            ipAddress
                    )))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response updateToken(
            @PathParam("id") UUID tokenId,
            @Valid UpdateApiTokenRequest updateRequest,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            String ipAddress = UserIpAddress.resolve(request, forwardedFor, realIp);
            return Response.ok(ApiResponse.success(apiTokenService.updateToken(
                    userId,
                    tokenId,
                    updateRequest.getName(),
                    updateRequest.getExpiresAt(),
                    ipAddress
            ))).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response revokeToken(
            @PathParam("id") UUID tokenId,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            String ipAddress = UserIpAddress.resolve(request, forwardedFor, realIp);
            apiTokenService.revokeOwnedToken(userId, tokenId, ipAddress);
            return Response.ok(ApiResponse.success("API token revoked")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        }
    }
}
