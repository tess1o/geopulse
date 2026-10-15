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
import io.quarkus.security.identity.SecurityIdentity;
import org.github.tess1o.geopulse.auth.model.ApiTokenScope;
import org.github.tess1o.geopulse.auth.security.RequiresApiTokenScope;
import org.github.tess1o.geopulse.auth.service.ApiTokenService;
import org.github.tess1o.geopulse.auth.service.IdentityAttributes;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;

import java.util.UUID;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.jboss.resteasy.reactive.RestResponse;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.API_TOKEN_INVALID;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.NOT_FOUND;

@Path("/api-tokens")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ADMIN"})
@Tag(name = ApiTags.API_TOKENS)
public class ApiTokenResource {

    @Context
    HttpServerRequest request;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    ApiTokenService apiTokenService;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    @Operation(summary = "List API tokens",
            description = "Returns the API tokens of the signed-in user with name, short preview, status, "
                    + "expiration, scopes, and last-use metadata. Token secrets are never returned. With `scope`, "
                    + "returns only active tokens created with that scope (for example `gps:ingest` to pick a token "
                    + "for sending locations).")
    public List<ApiTokenResponse> listTokens(
            @Parameter(description = "Only active tokens with this scope.", example = "gps:ingest")
            @QueryParam("scope") String scope) {
        UUID userId = currentUserService.getCurrentUserId();
        if (scope == null || scope.isBlank()) {
            return apiTokenService.listForUser(userId);
        }
        ApiTokenScope requested = ApiTokenScope.fromValue(scope)
                .orElseThrow(() -> new GeoPulseException(API_TOKEN_INVALID, "Unknown scope: " + scope));
        return apiTokenService.listActiveWithScope(userId, requested);
    }

    @GET
    @Path("/current")
    @RequiresApiTokenScope(ApiTokenScope.READ)
    @Operation(summary = "Get the calling API token",
            description = "Returns the API token used to authenticate this request, including its scopes, so a client "
                    + "can check what it is allowed to do. Works with a token of any scope. Returns 404 when the "
                    + "request is not authenticated with an API token.")
    public ApiTokenResponse currentToken() {
        String tokenId = securityIdentity.getAttribute(IdentityAttributes.API_TOKEN_ID);
        if (tokenId == null) {
            throw new GeoPulseException(NOT_FOUND, "This request is not authenticated with an API token");
        }
        return apiTokenService.findActiveForUser(currentUserService.getCurrentUserId(), UUID.fromString(tokenId))
                .orElseThrow(() -> new GeoPulseException(NOT_FOUND, "API token not found"));
    }

    @POST
    @Operation(summary = "Create an API token",
            description = "Creates an API token that acts as the signed-in user, limited to the requested scopes "
                    + "(`all` for full access). Scopes cannot be changed later. The response contains the full "
                    + "token secret; it is shown only once and cannot be retrieved later. Optionally set an "
                    + "expiration time.")
    public RestResponse<CreateApiTokenResponse> createToken(@Valid CreateApiTokenRequest createRequest) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            String ipAddress = UserIpAddress.resolve(request);
            return RestResponse.status(Response.Status.CREATED, apiTokenService.createToken(
                    userId,
                    createRequest.getName(),
                    createRequest.getExpiresAt(),
                    createRequest.getScopes(),
                    ipAddress
            ));
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(API_TOKEN_INVALID, API_TOKEN_INVALID.title(), e);
        }
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Rename an API token",
            description = "Changes the name or expiration time of an API token owned by the signed-in user.")
    public ApiTokenResponse updateToken(
            @Parameter(description = "API token ID.")
            @PathParam("id") UUID tokenId, @Valid UpdateApiTokenRequest updateRequest) {
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
    @Operation(summary = "Revoke an API token",
            description = "Revokes an API token owned by the signed-in user. Requests using the token are rejected "
                    + "from then on.")
    public void revokeToken(
            @Parameter(description = "API token ID.")
            @PathParam("id") UUID tokenId) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            String ipAddress = UserIpAddress.resolve(request);
            apiTokenService.revokeOwnedToken(userId, tokenId, ipAddress);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(API_TOKEN_INVALID, API_TOKEN_INVALID.title(), e);
        }
    }
}
