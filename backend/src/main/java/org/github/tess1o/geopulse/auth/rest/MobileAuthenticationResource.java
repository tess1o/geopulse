package org.github.tess1o.geopulse.auth.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.auth.service.MobileDeepLinkService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Slf4j
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "User: Authentication", description = "Create mobile authentication codes for the authenticated user.")
public class MobileAuthenticationResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    MobileDeepLinkService mobileDeepLinkService;

    @GET
    @Path("/mobile")
    @RolesAllowed({"USER", "ADMIN"})
    public Response generateCode() {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            var response = mobileDeepLinkService.generateAuthenticationLink(userId);
            return Response.ok(ApiResponse.success(response))
                    .build();
        } catch (Exception e) {
            log.error("Failed to generate authentication link", e);
        }
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiResponse.error("Unable to create authentication link"))
                .build();
    }
}
