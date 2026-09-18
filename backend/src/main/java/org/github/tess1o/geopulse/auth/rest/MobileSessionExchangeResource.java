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
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.model.MobileSessionExchangeRequest;
import org.github.tess1o.geopulse.auth.service.MobileDeepLinkService;

import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.BAD_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.MOBILE_SESSION_CODE_INVALID;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/auth/mobile-sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = "User: Authentication", description = "Exchange mobile session codes for authenticated sessions.")
public class MobileSessionExchangeResource {

    @Inject
    MobileDeepLinkService mobileDeepLinkService;

    @POST
    @APIResponseSchema(value = AuthResponse.class, responseCode = "200",
            responseDescription = "Authenticated mobile session")
    public Response exchangeSessionCode(@Valid MobileSessionExchangeRequest request) {
        if (request == null) {
            throw problem(BAD_REQUEST, "sessionCode is required");
        }

        Optional<AuthResponse> authResponse =
                mobileDeepLinkService.exchangeSessionCode(request.getSessionCode());

        if (authResponse.isEmpty()) {
            throw problem(MOBILE_SESSION_CODE_INVALID, "Mobile session code is expired or invalid");
        }

        return Response.ok(authResponse.get())
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .build();
    }
}
