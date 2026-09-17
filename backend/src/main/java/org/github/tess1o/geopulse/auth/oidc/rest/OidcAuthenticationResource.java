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
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.github.tess1o.geopulse.auth.config.AuthConfigurationService;
import org.github.tess1o.geopulse.auth.exceptions.OidcLoginDisabledException;
import org.github.tess1o.geopulse.auth.exceptions.OidcRegistrationDisabledException;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.model.BrowserAuthResponse;
import org.github.tess1o.geopulse.auth.oidc.dto.*;
import org.github.tess1o.geopulse.auth.exceptions.OidcAccountLinkingRequiredException;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;
import org.github.tess1o.geopulse.auth.oidc.service.OidcAuthenticationService;
import org.github.tess1o.geopulse.auth.oidc.service.OidcProviderService;
import org.github.tess1o.geopulse.auth.oidc.service.UserOidcConnectionService;
import org.github.tess1o.geopulse.auth.oidc.service.OidcAccountLinkingService;
import org.github.tess1o.geopulse.auth.service.BrowserAuthResponseMapper;
import org.github.tess1o.geopulse.auth.service.CookieService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import io.quarkiverse.httpproblem.HttpProblem;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

@Path("/api/auth/oidc")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Slf4j
@Tag(name = "User: Authentication", description = "Authenticate users and manage OIDC account linking.")
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
    BrowserAuthResponseMapper browserAuthResponseMapper;

    @Inject
    @ConfigProperty(name = "jwt.refresh-token.lifespan")
    @StaticInitSafe
    Long refreshTokenLifespan;
    
    @Inject
    OidcAccountLinkingService accountLinkingService;

    @Inject
    AuthConfigurationService authConfigurationService;

    /**
     * Get list of enabled OIDC providers
     */
    @GET
    @Path("/providers")
    public List<OidcProviderResponse> getEnabledProviders() {
        return providerService.getEnabledProviders().stream()
                .map(p -> OidcProviderResponse.builder()
                        .name(p.getName())
                        .displayName(p.getDisplayName())
                        .icon(p.getIcon())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Initiate OIDC login flow
     */
    @POST
    @Path("/login/{provider}")
    public OidcLoginInitResponse initiateLogin(
            @PathParam("provider") String providerName,
            @QueryParam("redirectUri") @DefaultValue("/app/timeline") String redirectUri) {
        try {
            // Check if OIDC login is enabled
            if (!authConfigurationService.isOidcLoginEnabled()) {
                throw problem(OIDC_LOGIN_DISABLED, "OIDC login is currently disabled");
            }
            return oidcAuthService.initiateLogin(providerName, null, redirectUri, null);
        } catch (IllegalArgumentException e) {
            throw problem(OIDC_PROVIDER_INVALID, e.getMessage());
        }
    }

    /**
     * Handle OIDC callback after provider authentication
     */
    @POST
    @Path("/callback")
    @APIResponseSchema(value = BrowserAuthResponse.class, responseCode = "200",
            responseDescription = "Authenticated browser session")
    public Response handleCallback(@Valid OidcCallbackRequest request) {
        try {
            OidcCallbackAuthResult callbackResult = oidcAuthService.handleCallback(request);
            AuthResponse authResponse = callbackResult.authResponse();

            // Create cookies similar to regular login
            var accessTokenCookie = cookieService.createAccessTokenCookie(
                    authResponse.getAccessToken(), authResponse.getExpiresIn());
            var refreshTokenCookie = cookieService.createRefreshTokenCookie(
                    authResponse.getRefreshToken(), refreshTokenLifespan);
            var tokenExpirationCookie = cookieService.createTokenExpirationCookie(authResponse.getExpiresIn());

            return Response.ok(browserAuthResponseMapper.toBrowserAuthResponse(authResponse, callbackResult.redirectUri()))
                    .cookie(accessTokenCookie)
                    .cookie(refreshTokenCookie)
                    .cookie(tokenExpirationCookie)
                    .build();
        } catch (OidcRegistrationDisabledException e) {
            throw problem(OIDC_REGISTRATION_DISABLED, e.getMessage());
        } catch (OidcLoginDisabledException e) {
            throw problem(OIDC_LOGIN_DISABLED, e.getMessage());
        } catch (OidcAccountLinkingRequiredException e) {
            // Handle account linking requirement
            OidcAccountLinkingErrorResponse errorResponse = OidcAccountLinkingErrorResponse.builder()
                    .email(e.getEmail())
                    .newProvider(e.getNewProvider())
                    .linkingToken(e.getLinkingToken())
                    .verificationMethods(OidcAccountLinkingErrorResponse.VerificationMethods.builder()
                            .password(e.isHasPassword())
                            .oidcProviders(e.getLinkedOidcProviders())
                            .build())
                    .build();
            
            throw HttpProblem.builder(problem(OIDC_ACCOUNT_LINKING_REQUIRED, "Account linking required"))
                    .with("linking", errorResponse)
                    .build();
        } catch (IllegalArgumentException e) {
            throw problem(OIDC_PROVIDER_INVALID, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to handle OIDC callback", e);
            throw problem(OIDC_AUTHENTICATION_FAILED, "OIDC authentication failed");
        }
    }

    /**
     * Initiate OIDC account linking for authenticated user
     */
    @POST
    @Path("/link/{provider}")
    @RolesAllowed({"USER", "ADMIN"})
    public OidcLoginInitResponse initiateLinking(
            @PathParam("provider") String providerName,
            @QueryParam("redirectUri") @DefaultValue("/app/profile") String redirectUri) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            return oidcAuthService.initiateLogin(providerName, userId, redirectUri, null);
        } catch (IllegalArgumentException e) {
            throw problem(OIDC_PROVIDER_INVALID, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to initiate OIDC linking for provider: {}", providerName, e);
            throw problem(OIDC_AUTHENTICATION_FAILED, "Failed to initiate OIDC account linking");
        }
    }

    /**
     * Unlink OIDC provider from authenticated user
     */
    @DELETE
    @Path("/unlink/{provider}")
    @RolesAllowed({"USER", "ADMIN"})
    public void unlinkProvider(@PathParam("provider") String providerName) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            userOidcConnectionService.unlinkProvider(userId, providerName);
        } catch (IllegalArgumentException e) {
            throw problem(OIDC_PROVIDER_INVALID, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to unlink OIDC provider: {}", providerName, e);
            throw problem(OIDC_AUTHENTICATION_FAILED, "Failed to unlink OIDC provider");
        }
    }

    /**
     * Get current user's OIDC connections
     */
    @GET
    @Path("/connections")
    @RolesAllowed({"USER", "ADMIN"})
    public List<UserOidcConnectionResponse> getUserConnections() {
        return userOidcConnectionService.getUserConnections(currentUserService.getCurrentUserId());
    }

    /**
     * Link OIDC account using password verification
     */
    @POST
    @Path("/link-with-password")
    @APIResponseSchema(value = BrowserAuthResponse.class, responseCode = "200",
            responseDescription = "Linked and authenticated browser session")
    public Response linkAccountWithPassword(@Valid LinkAccountWithPasswordRequest request) {
        try {
            AuthResponse authResponse = accountLinkingService.linkAccountWithPassword(request);
            
            // Create cookies for successful authentication
            var accessTokenCookie = cookieService.createAccessTokenCookie(
                    authResponse.getAccessToken(), authResponse.getExpiresIn());
            var refreshTokenCookie = cookieService.createRefreshTokenCookie(
                    authResponse.getRefreshToken(), refreshTokenLifespan);
            var tokenExpirationCookie = cookieService.createTokenExpirationCookie(authResponse.getExpiresIn());

            return Response.ok(browserAuthResponseMapper.toBrowserAuthResponse(authResponse, null))
                    .cookie(accessTokenCookie)
                    .cookie(refreshTokenCookie)
                    .cookie(tokenExpirationCookie)
                    .build();
        } catch (IllegalArgumentException e) {
            throw problem(OIDC_ACCOUNT_LINKING_FAILED, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to link account with password", e);
            throw problem(OIDC_AUTHENTICATION_FAILED, "Account linking failed");
        }
    }

    /**
     * Initiate OIDC-to-OIDC verification for account linking
     */
    @POST
    @Path("/link-with-oidc")
    public OidcLoginInitResponse linkAccountWithOidc(@Valid InitiateOidcLinkingRequest request) {
        try {
            return accountLinkingService.initiateOidcVerificationForLinking(request);
        } catch (IllegalArgumentException e) {
            throw problem(OIDC_ACCOUNT_LINKING_FAILED, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to initiate OIDC verification for linking", e);
            throw problem(OIDC_AUTHENTICATION_FAILED, "OIDC verification initiation failed");
        }
    }
}
