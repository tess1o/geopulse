package org.github.tess1o.geopulse.auth.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.auth.dto.CreateApiTokenRequest;
import org.github.tess1o.geopulse.auth.dto.ApiTokenResponse;
import org.github.tess1o.geopulse.auth.dto.CreateApiTokenResponse;
import org.github.tess1o.geopulse.auth.dto.UpdateApiTokenRequest;
import org.github.tess1o.geopulse.auth.service.ApiTokenService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;

import java.util.UUID;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.API_TOKEN_INVALID;

@Path("/api-tokens")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = "User: API Tokens", description = "Manage API tokens owned by the authenticated user.")
public class ApiTokenResource {

    @Context
    HttpServerRequest request;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ApiTokenService apiTokenService;

    @GET
    public List<ApiTokenResponse> listTokens() {
        UUID userId = currentUserService.getCurrentUserId();
        return apiTokenService.listForUser(userId);
    }

    @POST
    public RestResponse<CreateApiTokenResponse> createToken(@Valid CreateApiTokenRequest createRequest) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            String ipAddress = UserIpAddress.resolve(request);
            return RestResponse.status(Response.Status.CREATED, apiTokenService.createToken(
                    userId,
                    createRequest.getName(),
                    createRequest.getExpiresAt(),
                    ipAddress
            ));
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(API_TOKEN_INVALID, API_TOKEN_INVALID.title(), e);
        }
    }

    @PUT
    @Path("/{id}")
    public ApiTokenResponse updateToken(@PathParam("id") UUID tokenId, @Valid UpdateApiTokenRequest updateRequest) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            String ipAddress = UserIpAddress.resolve(request);
            return apiTokenService.updateToken(
                    userId,
                    tokenId,
                    updateRequest.getName(),
                    updateRequest.getExpiresAt(),
                    ipAddress
            );
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(API_TOKEN_INVALID, API_TOKEN_INVALID.title(), e);
        }
    }

    @DELETE
    @Path("/{id}")
    public void revokeToken(@PathParam("id") UUID tokenId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            String ipAddress = UserIpAddress.resolve(request);
            apiTokenService.revokeOwnedToken(userId, tokenId, ipAddress);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(API_TOKEN_INVALID, API_TOKEN_INVALID.title(), e);
        }
    }
}
