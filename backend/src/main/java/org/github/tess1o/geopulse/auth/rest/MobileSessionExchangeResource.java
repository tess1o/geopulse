package org.github.tess1o.geopulse.auth.rest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.model.MobileSessionExchangeRequest;
import org.github.tess1o.geopulse.auth.service.MobileDeepLinkService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/mobile")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "User: Authentication", description = "Exchange mobile session codes for authenticated sessions.")
public class MobileSessionExchangeResource {

    @Inject
    MobileDeepLinkService mobileDeepLinkService;

    @POST
    @Path("/session/exchange")
    public Response exchangeSessionCode(@Valid MobileSessionExchangeRequest request) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("sessionCode is required"))
                    .build();
        }

        Optional<AuthResponse> authResponse =
                mobileDeepLinkService.exchangeSessionCode(request.getSessionCode());

        if (authResponse.isEmpty()) {
            return Response.status(Response.Status.GONE)
                    .entity(ApiResponse.error("Mobile session code is expired or invalid"))
                    .build();
        }

        return Response.ok(ApiResponse.success(authResponse.get()))
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .build();
    }
}
