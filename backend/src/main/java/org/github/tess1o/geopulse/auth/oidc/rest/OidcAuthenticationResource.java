package org.github.tess1o.geopulse.auth.oidc.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.github.tess1o.geopulse.shared.openapi.ApiExtensions;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;

@Path("/auth/oidc")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Tag(name = ApiTags.OIDC_SIGN_IN)
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
    @Operation(summary = "List OIDC providers",
            description = "Returns the OpenID Connect providers users can sign in with, including their display "
                    + "names and icons.")
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
    @Path("/login-authorizations/{provider}")
    @Operation(summary = "Start OIDC sign-in",
            description = "Starts signing in with an OIDC provider. Returns the provider authorization URL to "
                    + "redirect the browser to. After the user signs in at the provider, the provider redirects "
                    + "back to the GeoPulse web app, which completes the flow with `POST "
                    + "/api/v1/auth/oidc/callbacks`.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public OidcLoginInitResponse initiateLogin(
            @Parameter(description = "Provider name, as returned by `GET /api/v1/auth/oidc/providers`.",
                    example = "google")
            @PathParam("provider") String providerName,
            @Parameter(description = "Web app path to open after sign-in completes. Defaults to `/app/timeline`.",
                    example = "/app/timeline")
            @QueryParam("redirectUri") @DefaultValue("/app/timeline") String redirectUri) {
        try {
            // Check if OIDC login is enabled
            if (!authConfigurationService.isOidcLoginEnabled()) {
                throw new GeoPulseException(OIDC_LOGIN_DISABLED, "OIDC login is currently disabled");
            }
            return oidcAuthService.initiateLogin(providerName, null, redirectUri, null);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(OIDC_PROVIDER_INVALID, OIDC_PROVIDER_INVALID.title(), e);
        }
    }

    /**
     * Handle OIDC callback after provider authentication
     */
    @POST
    @Path("/callbacks")
    @APIResponseSchema(value = BrowserAuthResponse.class, responseCode = "200",
            responseDescription = "Authenticated browser session")
    @Operation(summary = "Complete OIDC sign-in",
            description = "Completes an OIDC sign-in or account-linking flow with the authorization code and state "
                    + "the provider returned. On success, sets the session cookies like password sign-in. A new "
                    + "account is created on first sign-in when OIDC registration is enabled. If an account with "
                    + "the same email already exists, the request fails with `OIDC_ACCOUNT_LINKING_REQUIRED` and "
                    + "the error contains a linking token for `POST /api/v1/auth/oidc/account-links/password` or "
                    + "`POST /api/v1/auth/oidc/account-links/oidc`.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
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
            throw new GeoPulseException(OIDC_REGISTRATION_DISABLED, OIDC_REGISTRATION_DISABLED.title(), e);
        } catch (OidcLoginDisabledException e) {
            throw new GeoPulseException(OIDC_LOGIN_DISABLED, OIDC_LOGIN_DISABLED.title(), e);
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
            
            throw new GeoPulseException(OIDC_ACCOUNT_LINKING_REQUIRED, "Account linking required",
                    Map.of("linking", errorResponse), e);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(OIDC_PROVIDER_INVALID, OIDC_PROVIDER_INVALID.title(), e);
        }
    }

    /**
     * Initiate OIDC account linking for authenticated user
     */
    @POST
    @Path("/connections/{provider}/authorizations")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Start linking an OIDC provider",
            description = "Starts linking an OIDC provider to the signed-in account. Returns the provider "
                    + "authorization URL; the flow completes through `POST /api/v1/auth/oidc/callbacks`.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public OidcLoginInitResponse initiateLinking(
            @Parameter(description = "Provider name to link.", example = "google")
            @PathParam("provider") String providerName,
            @Parameter(description = "Web app path to open after linking completes. Defaults to `/app/profile`.",
                    example = "/app/profile")
            @QueryParam("redirectUri") @DefaultValue("/app/profile") String redirectUri) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            return oidcAuthService.initiateLogin(providerName, userId, redirectUri, null);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(OIDC_PROVIDER_INVALID, OIDC_PROVIDER_INVALID.title(), e);
        }
    }

    /**
     * Unlink OIDC provider from authenticated user
     */
    @DELETE
    @Path("/connections/{provider}")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "Unlink an OIDC provider",
            description = "Removes the link between the signed-in account and an OIDC provider.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public void unlinkProvider(
            @Parameter(description = "Provider name to unlink.", example = "google")
            @PathParam("provider") String providerName) {
        try {
            UUID userId = currentUserService.getCurrentUserId();
            userOidcConnectionService.unlinkProvider(userId, providerName);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(OIDC_PROVIDER_INVALID, OIDC_PROVIDER_INVALID.title(), e);
        }
    }

    /**
     * Get current user's OIDC connections
     */
    @GET
    @Path("/connections")
    @RolesAllowed({"USER", "ADMIN"})
    @Operation(summary = "List linked OIDC providers",
            description = "Returns the OIDC providers linked to the signed-in account.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public List<UserOidcConnectionResponse> getUserConnections() {
        return userOidcConnectionService.getUserConnections(currentUserService.getCurrentUserId());
    }

    /**
     * Link OIDC account using password verification
     */
    @POST
    @Path("/account-links/password")
    @APIResponseSchema(value = BrowserAuthResponse.class, responseCode = "200",
            responseDescription = "Linked and authenticated browser session")
    @Operation(summary = "Link OIDC identity by password",
            description = "Confirms an account-linking request by verifying the existing account password. Use the "
                    + "linking token from the `OIDC_ACCOUNT_LINKING_REQUIRED` error. On success, the OIDC identity "
                    + "is linked and the user is signed in.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
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
            throw new GeoPulseException(OIDC_ACCOUNT_LINKING_FAILED, OIDC_ACCOUNT_LINKING_FAILED.title(), e);
        }
    }

    /**
     * Initiate OIDC-to-OIDC verification for account linking
     */
    @POST
    @Path("/account-links/oidc")
    @Operation(summary = "Link OIDC identity by another provider",
            description = "Confirms an account-linking request by signing in with a provider that is already linked "
                    + "to the existing account. Returns the authorization URL of that provider; the flow completes "
                    + "through `POST /api/v1/auth/oidc/callbacks`.")
    @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)
    public OidcLoginInitResponse linkAccountWithOidc(@Valid InitiateOidcLinkingRequest request) {
        try {
            return accountLinkingService.initiateOidcVerificationForLinking(request);
        } catch (IllegalArgumentException e) {
            throw new GeoPulseException(OIDC_ACCOUNT_LINKING_FAILED, OIDC_ACCOUNT_LINKING_FAILED.title(), e);
        }
    }
}
