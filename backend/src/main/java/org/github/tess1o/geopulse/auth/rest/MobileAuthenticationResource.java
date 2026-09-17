package org.github.tess1o.geopulse.auth.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.auth.service.MobileDeepLinkService;
import org.github.tess1o.geopulse.auth.model.MobileAuthInitResponse;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.AUTHENTICATION_FAILED;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

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
    public MobileAuthInitResponse generateCode() {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            return mobileDeepLinkService.generateAuthenticationLink(userId);
        } catch (Exception e) {
            log.error("Failed to generate authentication link", e);
            throw problem(AUTHENTICATION_FAILED, "Unable to create authentication link");
        }
    }
}
