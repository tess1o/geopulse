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
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = ApiTags.MOBILE_SIGN_IN)
public class MobileAuthenticationResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    MobileDeepLinkService mobileDeepLinkService;

    @POST
    @Path("/mobile-codes")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Create a mobile sign-in code",
            description = "Creates a short-lived, one-time code and a deep link that signs the GeoPulse mobile app "
                    + "in as the current user. Open the deep link on the phone, or pass the code to `POST "
                    + "/api/v1/auth/mobile-sessions`.")
    public MobileAuthInitResponse generateCode() {
        UUID userId = currentUserService.getCurrentUserId();
        return mobileDeepLinkService.generateAuthenticationLink(userId);
    }
}
