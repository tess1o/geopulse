package org.github.tess1o.geopulse.auth.oidc.rest;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.auth.exceptions.OidcRegistrationDisabledException;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.oidc.dto.*;
import org.github.tess1o.geopulse.auth.exceptions.OidcAccountLinkingRequiredException;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;
import org.github.tess1o.geopulse.auth.oidc.service.OidcAuthenticationService;
import org.github.tess1o.geopulse.auth.oidc.service.OidcProviderService;
import org.github.tess1o.geopulse.auth.oidc.service.UserOidcConnectionService;
import org.github.tess1o.geopulse.auth.oidc.service.OidcAccountLinkingService;
import org.github.tess1o.geopulse.auth.service.CookieService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/auth/oidc")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
public class OidcAuthenticationResource {

    @Inject
    OidcAuthenticationService oidcAuthService;

    @Inject
    OidcProviderService providerService;

    @Inject
    UserOidcConnectionService userOidcConnectionService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    CookieService cookieService;

    @Inject
    @ConfigProperty(name = "jwt.refresh-token.lifespan")
    @StaticInitSafe
    Long refreshTokenLifespan;
    
    @Inject
    OidcAccountLinkingService accountLinkingService;

    /**
     * Get list of enabled OIDC providers
     */
    @GET
    @Path("/providers")
    public Response getEnabledProviders() {
        try {
            List<OidcProviderConfiguration> providers = providerService.getEnabledProviders();
            List<OidcProviderResponse> response = providers.stream()
                    .map(p -> OidcProviderResponse.builder()
                            .name(p.getName())
                            .displayName(p.getDisplayName())
                            .icon(p.getIcon())
                            .build())
                    .collect(Collectors.toList());

            return Response.ok(ApiResponse.success(response)).build();
        } catch (Exception e) {
            log.error("Failed to get OIDC providers", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get OIDC providers"))
                    .build();
        }
    }

    /**
     * Initiate OIDC login flow
     */
    @POST
    @Path("/login/{provider}")
    public Response initiateLogin(@PathParam("provider") String providerName,
                                  @QueryParam("redirectUri") @DefaultValue("/app/timeline") String redirectUri) {
        try {
            OidcLoginInitResponse response = oidcAuthService.initiateLogin(providerName, null, redirectUri, null);
            return Response.ok(ApiResponse.success(response)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to initiate OIDC login for provider: {}", providerName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to initiate OIDC login"))
                    .build();
        }
    }

    /**
     * Handle OIDC callback after provider authentication
     */
    @POST
    @Path("/callback")
    public Response handleCallback(@Valid OidcCallbackRequest request) {
        try {
            AuthResponse authResponse = oidcAuthService.handleCallback(request);

            // Create cookies similar to regular login
            var accessTokenCookie = cookieService.createAccessTokenCookie(
                    authResponse.getAccessToken(), authResponse.getExpiresIn());
            var refreshTokenCookie = cookieService.createRefreshTokenCookie(
                    authResponse.getRefreshToken(), refreshTokenLifespan);
            var tokenExpirationCookie = cookieService.createTokenExpirationCookie(authResponse.getExpiresIn());

            return Response.ok(ApiResponse.success(authResponse))
                    .cookie(accessTokenCookie)
                    .cookie(refreshTokenCookie)
                    .cookie(tokenExpirationCookie)
                    .build();
        } catch (OidcRegistrationDisabledException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (OidcAccountLinkingRequiredException e) {
            // Handle account linking requirement
            OidcAccountLinkingErrorResponse errorResponse = OidcAccountLinkingErrorResponse.builder()
                    .error("ACCOUNT_LINKING_REQUIRED")
                    .email(e.getEmail())
                    .newProvider(e.getNewProvider())
                    .linkingToken(e.getLinkingToken())
                    .message("Account with this email already exists. Please verify your identity to link this OIDC account.")
                    .verificationMethods(OidcAccountLinkingErrorResponse.VerificationMethods.builder()
                            .password(e.isHasPassword())
                            .oidcProviders(e.getLinkedOidcProviders())
                            .build())
                    .build();
            
            return Response.status(Response.Status.CONFLICT)
                    .entity(ApiResponse.error("Account linking required", errorResponse))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to handle OIDC callback", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("OIDC authentication failed"))
                    .build();
        }
    }

    /**
     * Initiate OIDC account linking for authenticated user
     */
    @POST
    @Path("/link/{provider}")
    @RolesAllowed("USER")
    public Response initiateLinking(@PathParam("provider") String providerName,
                                    @QueryParam("redirectUri") @DefaultValue("/app/profile") String redirectUri) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            OidcLoginInitResponse response = oidcAuthService.initiateLogin(providerName, userId, redirectUri, null);
            return Response.ok(ApiResponse.success(response)).build();
        } catch (Exception e) {
            log.error("Failed to initiate OIDC linking for provider: {}", providerName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to initiate OIDC account linking"))
                    .build();
        }
    }

    /**
     * Unlink OIDC provider from authenticated user
     */
    @DELETE
    @Path("/unlink/{provider}")
    @RolesAllowed("USER")
    public Response unlinkProvider(@PathParam("provider") String providerName) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            userOidcConnectionService.unlinkProvider(userId, providerName);
            return Response.ok(ApiResponse.success("Provider unlinked successfully")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to unlink OIDC provider: {}", providerName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to unlink OIDC provider"))
                    .build();
        }
    }

    /**
     * Get current user's OIDC connections
     */
    @GET
    @Path("/connections")
    @RolesAllowed("USER")
    public Response getUserConnections() {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            List<UserOidcConnectionResponse> connections = userOidcConnectionService.getUserConnections(userId);
            return Response.ok(ApiResponse.success(connections)).build();
        } catch (Exception e) {
            log.error("Failed to get user OIDC connections", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to get OIDC connections"))
                    .build();
        }
    }

    /**
     * Link OIDC account using password verification
     */
    @POST
    @Path("/link-with-password")
    public Response linkAccountWithPassword(@Valid LinkAccountWithPasswordRequest request) {
        try {
            AuthResponse authResponse = accountLinkingService.linkAccountWithPassword(request);
            
            // Create cookies for successful authentication
            var accessTokenCookie = cookieService.createAccessTokenCookie(
                    authResponse.getAccessToken(), authResponse.getExpiresIn());
            var refreshTokenCookie = cookieService.createRefreshTokenCookie(
                    authResponse.getRefreshToken(), refreshTokenLifespan);
            var tokenExpirationCookie = cookieService.createTokenExpirationCookie(authResponse.getExpiresIn());

            return Response.ok(ApiResponse.success(authResponse))
                    .cookie(accessTokenCookie)
                    .cookie(refreshTokenCookie)
                    .cookie(tokenExpirationCookie)
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to link account with password", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Account linking failed"))
                    .build();
        }
    }

    /**
     * Initiate OIDC-to-OIDC verification for account linking
     */
    @POST
    @Path("/link-with-oidc")
    public Response linkAccountWithOidc(@Valid InitiateOidcLinkingRequest request) {
        try {
            OidcLoginInitResponse response = accountLinkingService.initiateOidcVerificationForLinking(request);
            return Response.ok(ApiResponse.success(response)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to initiate OIDC verification for linking", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("OIDC verification initiation failed"))
                    .build();
        }
    }
}