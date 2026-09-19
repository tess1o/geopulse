package org.github.tess1o.geopulse.auth.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.auth.service.MobileDeepLinkService;
import org.github.tess1o.geopulse.auth.model.MobileAuthInitResponse;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "User: Authentication", description = "Create mobile authentication codes for the authenticated user.")
public class MobileAuthenticationResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    MobileDeepLinkService mobileDeepLinkService;

    @POST
    @Path("/mobile-codes")
    @RolesAllowed({"USER", "ADMIN"})
    public MobileAuthInitResponse generateCode() {
        UUID userId = currentUserService.getCurrentUserId();
        return mobileDeepLinkService.generateAuthenticationLink(userId);
    }
}
