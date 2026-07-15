package org.github.tess1o.geopulse.admin.rest;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.admin.dto.PagedResponse;
import org.github.tess1o.geopulse.auth.dto.ApiTokenResponse;
import org.github.tess1o.geopulse.auth.model.ApiTokenStatus;
import org.github.tess1o.geopulse.auth.service.ApiTokenService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/admin/api-tokens")
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
    public Response listTokens(
            @QueryParam("userId") UUID userId,
            @QueryParam("status") ApiTokenStatus status,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size) {
        List<ApiTokenResponse> tokens = apiTokenService.listForAdmin(userId, status, page, size);
        long total = apiTokenService.countForAdmin(userId, status);

        PagedResponse<ApiTokenResponse> response = PagedResponse.<ApiTokenResponse>builder()
                .content(tokens)
                .totalElements(total)
                .totalPages((int) Math.ceil((double) total / size))
                .page(page)
                .size(size)
                .build();
        return Response.ok(response).build();
    }

    @DELETE
    @Path("/{id}")
    public Response revokeToken(
            @PathParam("id") UUID tokenId,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {
        try {
            UUID adminUserId = currentUserService.getCurrentUserId();
            String ipAddress = UserIpAddress.resolve(request, forwardedFor, realIp);
            apiTokenService.revokeTokenAsAdmin(adminUserId, tokenId, ipAddress);
            return Response.ok(Map.of("success", true)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
