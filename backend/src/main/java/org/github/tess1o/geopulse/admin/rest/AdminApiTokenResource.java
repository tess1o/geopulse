package org.github.tess1o.geopulse.admin.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.auth.dto.ApiTokenResponse;
import org.github.tess1o.geopulse.auth.model.ApiTokenStatus;
import org.github.tess1o.geopulse.auth.service.ApiTokenService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.PageResponse;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;

import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.API_TOKEN_INVALID;

@Path("/admin/api-tokens")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@Tag(name = ApiTags.ADMIN_API_TOKENS)
public class AdminApiTokenResource {

    @Context
    HttpServerRequest request;

    @Inject
    ApiTokenService apiTokenService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    @Operation(summary = "List all API tokens",
            description = "Returns API tokens of all users one page at a time, with owner, status, expiration, and "
                    + "last use. Token secrets are never returned.")
    public PageResponse<ApiTokenResponse> listTokens(
            @Parameter(description = "Only tokens owned by this user.")
            @QueryParam("userId") UUID userId,
            @Parameter(description = "Only tokens with this status: `ACTIVE`, `EXPIRED`, or `REVOKED`.",
                    example = "ACTIVE")
            @QueryParam("status") ApiTokenStatus status,
            @Parameter(description = "Page number, starting at 0.")
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @Parameter(description = "Page size, from 1 to 200. Defaults to 50.")
            @QueryParam("size") @DefaultValue("50") @Min(1) @Max(200) int size) {
        List<ApiTokenResponse> tokens = apiTokenService.listForAdmin(userId, status, page, size);
        long total = apiTokenService.countForAdmin(userId, status);

        return new PageResponse<>(tokens, page, size, total, (int) Math.ceil((double) total / size));
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Revoke a user's API token",
            description = "Revokes any user's API token, for example after a leak. The revocation is recorded in the "
                    + "audit log.")
    public void revokeToken(
            @Parameter(description = "API token ID.")
            @PathParam("id") UUID tokenId) {
        try {
            UUID adminUserId = currentUserService.getCurrentUserId();
            String ipAddress = UserIpAddress.resolve(request);
            apiTokenService.revokeTokenAsAdmin(adminUserId, tokenId, ipAddress);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(API_TOKEN_INVALID, API_TOKEN_INVALID.title(), e);
        }
    }
}
