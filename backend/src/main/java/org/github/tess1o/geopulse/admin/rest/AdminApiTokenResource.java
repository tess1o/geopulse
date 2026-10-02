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

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.API_TOKEN_INVALID;

@Path("/admin/api-tokens")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@Tag(name = "Admin: API Tokens", description = "Administer API tokens across all users.")
public class AdminApiTokenResource {

    @Context
    HttpServerRequest request;

    @Inject
    ApiTokenService apiTokenService;

    @Inject
    CurrentUserService currentUserService;

    @GET
    public PageResponse<ApiTokenResponse> listTokens(
            @QueryParam("userId") UUID userId,
            @QueryParam("status") ApiTokenStatus status,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("50") @Min(1) @Max(200) int size) {
        List<ApiTokenResponse> tokens = apiTokenService.listForAdmin(userId, status, page, size);
        long total = apiTokenService.countForAdmin(userId, status);

        return new PageResponse<>(tokens, page, size, total, (int) Math.ceil((double) total / size));
    }

    @DELETE
    @Path("/{id}")
    public void revokeToken(@PathParam("id") UUID tokenId) {
        try {
            UUID adminUserId = currentUserService.getCurrentUserId();
            String ipAddress = UserIpAddress.resolve(request);
            apiTokenService.revokeTokenAsAdmin(adminUserId, tokenId, ipAddress);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(API_TOKEN_INVALID, API_TOKEN_INVALID.title(), e);
        }
    }
}
