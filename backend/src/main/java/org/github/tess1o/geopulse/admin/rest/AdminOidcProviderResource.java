package org.github.tess1o.geopulse.admin.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.github.tess1o.geopulse.admin.dto.*;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.admin.service.OidcProviderConfigurationService;
import org.github.tess1o.geopulse.auth.oidc.model.OidcDiscoveryDocument;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST resource for OIDC provider management.
 */
@Path("/api/admin/oidc/providers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@Slf4j
public class AdminOidcProviderResource {

    @Inject
    OidcProviderConfigurationService configurationService;

    @Inject
    AuditLogService auditLogService;

    @Inject
    CurrentUserService currentUserService;

    /**
     * Get all OIDC providers (from DB and environment).
     */
    @GET
    public Response getAllProviders() {
        List<OidcProviderConfiguration> providers = configurationService.loadAllProviders();

        List<OidcProviderResponse> response = providers.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return Response.ok(response).build();
    }

    /**
     * Get a single OIDC provider by name.
     */
    @GET
    @Path("/{name}")
    public Response getProvider(@PathParam("name") String name) {
        return configurationService.getProviderByName(name)
                .map(this::mapToResponse)
                .map(response -> Response.ok(response).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Provider not found"))
                        .build());
    }

    /**
     * Create a new OIDC provider.
     */
    @POST
    public Response createProvider(
            @Valid CreateOidcProviderRequest request,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {

        UUID adminId = currentUserService.getCurrentUserId();

        // Check if provider already exists
        if (configurationService.getProviderByName(request.getName()).isPresent()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "Provider with this name already exists"))
                    .build();
        }

        try {
            // Build provider configuration
            OidcProviderConfiguration provider = OidcProviderConfiguration.builder()
                    .name(request.getName())
                    .displayName(request.getDisplayName())
                    .enabled(request.isEnabled())
                    .clientId(request.getClientId())
                    .clientSecret(request.getClientSecret())
                    .discoveryUrl(request.getDiscoveryUrl())
                    .icon(request.getIcon())
                    .scopes(request.getScopes())
                    .metadataValid(false)
                    .build();

            // Save to database
            OidcProviderConfiguration saved = configurationService.saveProvider(provider, adminId);

            // Audit log
            String ipAddress = forwardedFor != null ? forwardedFor : realIp;
            auditLogService.logAction(
                    adminId,
                    ActionType.OIDC_PROVIDER_CREATED,
                    TargetType.OIDC_PROVIDER,
                    saved.getName(),
                    Map.of(
                            "displayName", saved.getDisplayName(),
                            "enabled", saved.isEnabled(),
                            "discoveryUrl", saved.getDiscoveryUrl()
                    ),
                    ipAddress
            );

            return Response.status(Response.Status.CREATED)
                    .entity(mapToResponse(saved))
                    .build();

        } catch (Exception e) {
            log.error("Failed to create OIDC provider: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to create provider: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Update an existing OIDC provider.
     */
    @PUT
    @Path("/{name}")
    public Response updateProvider(
            @PathParam("name") String name,
            @Valid UpdateOidcProviderRequest request,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {

        UUID adminId = currentUserService.getCurrentUserId();

        try {
            // Get existing provider (from DB or env)
            OidcProviderConfiguration existing = configurationService.getProviderByName(name)
                    .orElseThrow(() -> new NotFoundException("Provider not found: " + name));

            // Capture old state for audit
            Map<String, Object> oldState = new HashMap<>();
            oldState.put("displayName", existing.getDisplayName());
            oldState.put("enabled", existing.isEnabled());
            oldState.put("clientId", existing.getClientId());
            oldState.put("discoveryUrl", existing.getDiscoveryUrl());

            // Update provider
            OidcProviderConfiguration updated = OidcProviderConfiguration.builder()
                    .name(name)
                    .displayName(request.getDisplayName())
                    .enabled(request.isEnabled())
                    .clientId(request.getClientId())
                    .clientSecret(
                            request.getClientSecret() != null && !request.getClientSecret().isEmpty()
                                    ? request.getClientSecret()
                                    : existing.getClientSecret()
                    )
                    .discoveryUrl(request.getDiscoveryUrl())
                    .icon(request.getIcon())
                    .scopes(request.getScopes())
                    .metadataValid(false) // Invalidate metadata on update
                    .build();

            // Save to database
            OidcProviderConfiguration saved = configurationService.saveProvider(updated, adminId);

            // Capture new state for audit
            Map<String, Object> newState = new HashMap<>();
            newState.put("displayName", saved.getDisplayName());
            newState.put("enabled", saved.isEnabled());
            newState.put("clientId", saved.getClientId());
            newState.put("discoveryUrl", saved.getDiscoveryUrl());

            // Audit log
            String ipAddress = forwardedFor != null ? forwardedFor : realIp;
            auditLogService.logAction(
                    adminId,
                    ActionType.OIDC_PROVIDER_UPDATED,
                    TargetType.OIDC_PROVIDER,
                    saved.getName(),
                    Map.of("oldState", oldState, "newState", newState),
                    ipAddress
            );

            return Response.ok(mapToResponse(saved)).build();

        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to update OIDC provider: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to update provider: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Delete an OIDC provider from database.
     * If provider exists in environment, it will revert to env configuration.
     */
    @DELETE
    @Path("/{name}")
    public Response deleteProvider(
            @PathParam("name") String name,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {

        UUID adminId = currentUserService.getCurrentUserId();

        // Check if provider exists in database
        if (!configurationService.existsInDatabase(name)) {
            // Provider is from environment only, cannot delete
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "error", "Cannot delete environment-based provider. " +
                                    "Provider must be removed from environment variables."
                    ))
                    .build();
        }

        try {
            // Capture provider details for audit before deletion
            OidcProviderConfiguration provider = configurationService.getProviderByName(name)
                    .orElseThrow(() -> new NotFoundException("Provider not found: " + name));

            Map<String, Object> providerDetails = new HashMap<>();
            providerDetails.put("displayName", provider.getDisplayName());
            providerDetails.put("enabled", provider.isEnabled());
            providerDetails.put("clientId", provider.getClientId());
            providerDetails.put("discoveryUrl", provider.getDiscoveryUrl());

            // Delete from database
            configurationService.deleteProvider(name);

            // Audit log
            String ipAddress = forwardedFor != null ? forwardedFor : realIp;
            auditLogService.logAction(
                    adminId,
                    ActionType.OIDC_PROVIDER_DELETED,
                    TargetType.OIDC_PROVIDER,
                    name,
                    providerDetails,
                    ipAddress
            );

            // Check if provider still exists in environment
            boolean stillExists = configurationService.isFromEnvironment(name);

            return Response.ok(Map.of(
                    "success", true,
                    "message", stillExists
                            ? "Provider deleted from database and reverted to environment configuration"
                            : "Provider deleted successfully"
            )).build();

        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to delete OIDC provider: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to delete provider: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Reset an OIDC provider to environment variable configuration.
     */
    @POST
    @Path("/{name}/reset")
    public Response resetProvider(
            @PathParam("name") String name,
            @HeaderParam("X-Forwarded-For") String forwardedFor,
            @HeaderParam("X-Real-IP") String realIp) {

        UUID adminId = currentUserService.getCurrentUserId();

        // Check if provider exists in environment
        if (!configurationService.isFromEnvironment(name)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Provider does not exist in environment variables"))
                    .build();
        }

        try {
            // Delete from database to revert to env
            configurationService.deleteProvider(name);

            // Audit log
            String ipAddress = forwardedFor != null ? forwardedFor : realIp;
            auditLogService.logAction(
                    adminId,
                    ActionType.OIDC_PROVIDER_RESET,
                    TargetType.OIDC_PROVIDER,
                    name,
                    Map.of("action", "reset to environment defaults"),
                    ipAddress
            );

            // Return the environment provider configuration
            OidcProviderConfiguration envProvider = configurationService.getProviderByName(name)
                    .orElseThrow(() -> new RuntimeException("Failed to load environment provider after reset"));

            return Response.ok(mapToResponse(envProvider)).build();

        } catch (Exception e) {
            log.error("Failed to reset OIDC provider: {}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to reset provider: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Test connection to an OIDC provider's discovery endpoint.
     */
    @POST
    @Path("/{name}/test")
    public Response testProvider(@PathParam("name") String name) {
        try {
            // Get provider configuration
            OidcProviderConfiguration provider = configurationService.getProviderByName(name)
                    .orElseThrow(() -> new NotFoundException("Provider not found: " + name));

            // Attempt to fetch discovery document
            OidcDiscoveryDocument discovery = RestClientBuilder.newBuilder()
                    .baseUri(URI.create(provider.getDiscoveryUrl()))
                    .build(OidcDiscoveryClient.class)
                    .getDiscoveryDocument();

            // Build success response
            TestOidcProviderResponse response = TestOidcProviderResponse.success(
                    discovery.getAuthorizationEndpoint(),
                    discovery.getTokenEndpoint(),
                    discovery.getUserinfoEndpoint(),
                    discovery.getJwksUri(),
                    discovery.getIssuer()
            );

            return Response.ok(response).build();

        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(TestOidcProviderResponse.failure("NOT_FOUND", e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to test OIDC provider connection: {}", e.getMessage(), e);
            return Response.ok(
                    TestOidcProviderResponse.failure(
                            e.getClass().getSimpleName(),
                            e.getMessage()
                    )
            ).build();
        }
    }

    /**
     * Map OidcProviderConfiguration to response DTO.
     */
    private OidcProviderResponse mapToResponse(OidcProviderConfiguration provider) {
        // Determine source and environment config
        boolean isInDb = configurationService.existsInDatabase(provider.getName());
        boolean isInEnv = configurationService.isFromEnvironment(provider.getName());

        OidcProviderResponse.ProviderSource source =
                isInDb ? OidcProviderResponse.ProviderSource.DATABASE :
                        (isInEnv ? OidcProviderResponse.ProviderSource.ENVIRONMENT :
                                OidcProviderResponse.ProviderSource.DATABASE);

        return OidcProviderResponse.builder()
                .name(provider.getName())
                .displayName(provider.getDisplayName())
                .enabled(provider.isEnabled())
                .clientId(provider.getClientId())
                .hasClientSecret(provider.getClientSecret() != null && !provider.getClientSecret().isEmpty())
                .discoveryUrl(provider.getDiscoveryUrl())
                .icon(provider.getIcon())
                .scopes(provider.getScopes())
                .source(source)
                .hasEnvironmentConfig(isInEnv) // Flag to indicate if env vars exist
                .metadataValid(provider.isMetadataValid())
                .metadataCachedAt(provider.getMetadataCachedAt())
                .build();
    }

    /**
     * REST client interface for OIDC discovery document.
     */
    @jakarta.ws.rs.Path("/")
    public interface OidcDiscoveryClient {
        @GET
        @Produces(MediaType.APPLICATION_JSON)
        OidcDiscoveryDocument getDiscoveryDocument();
    }
}
