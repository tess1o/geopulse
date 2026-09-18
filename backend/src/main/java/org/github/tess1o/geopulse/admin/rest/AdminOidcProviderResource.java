package org.github.tess1o.geopulse.admin.rest;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.github.tess1o.geopulse.admin.dto.*;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.admin.service.OidcProviderConfigurationService;
import org.github.tess1o.geopulse.auth.security.SecurityRoles;
import org.github.tess1o.geopulse.auth.oidc.model.OidcDiscoveryDocument;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.UserIpAddress;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.*;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

/**
 * REST resource for OIDC provider management.
 */
@Path("/admin/oidc-providers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@Tag(name = "Admin: OIDC Providers", description = "Manage OpenID Connect provider configuration.")
public class AdminOidcProviderResource {

    @Context
    HttpServerRequest httpRequest;

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
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    public List<OidcProviderResponse> getAllProviders() {
        List<OidcProviderConfiguration> providers = configurationService.loadAllProviders();

        return providers.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get a single OIDC provider by name.
     */
    @GET
    @Path("/{name}")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.DEMO_ADMIN_READ})
    public OidcProviderResponse getProvider(@PathParam("name") String name) {
        return configurationService.getProviderByName(name)
                .map(this::mapToResponse)
                .orElseThrow(() -> problem(OIDC_PROVIDER_NOT_FOUND, "Provider not found"));
    }

    /**
     * Create a new OIDC provider.
     */
    @POST
    @RolesAllowed(SecurityRoles.ADMIN)
    public RestResponse<OidcProviderResponse> createProvider(@Valid CreateOidcProviderRequest request) {

        UUID adminId = currentUserService.getCurrentUserId();

        // Check if provider already exists
        if (configurationService.getProviderByName(request.getName()).isPresent()) {
            throw problem(OIDC_PROVIDER_CONFLICT, "Provider with this name already exists",
                    Map.of("name", request.getName()));
        }

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

        OidcProviderConfiguration saved = configurationService.saveProvider(provider, adminId);

        // Audit log
        String ipAddress = UserIpAddress.resolve(httpRequest);
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

        return RestResponse.status(Response.Status.CREATED, mapToResponse(saved));
    }

    /**
     * Update an existing OIDC provider.
     */
    @PUT
    @Path("/{name}")
    @RolesAllowed(SecurityRoles.ADMIN)
    public OidcProviderResponse updateProvider(@PathParam("name") String name,
                                               @Valid UpdateOidcProviderRequest request) {

        UUID adminId = currentUserService.getCurrentUserId();

        OidcProviderConfiguration existing = configurationService.getProviderByName(name)
                .orElseThrow(() -> problem(OIDC_PROVIDER_NOT_FOUND, "Provider not found",
                        Map.of("name", name)));

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
        String ipAddress = UserIpAddress.resolve(httpRequest);
        auditLogService.logAction(
                adminId,
                ActionType.OIDC_PROVIDER_UPDATED,
                TargetType.OIDC_PROVIDER,
                saved.getName(),
                Map.of("oldState", oldState, "newState", newState),
                ipAddress
        );

        return mapToResponse(saved);
    }

    /**
     * Delete an OIDC provider from database.
     * If provider exists in environment, it will revert to env configuration.
     */
    @DELETE
    @Path("/{name}")
    @RolesAllowed(SecurityRoles.ADMIN)
    public void deleteProvider(@PathParam("name") String name) {

        UUID adminId = currentUserService.getCurrentUserId();

        // Check if provider exists in database
        if (!configurationService.existsInDatabase(name)) {
            throw problem(OIDC_PROVIDER_ENVIRONMENT_ONLY,
                    "Cannot delete environment-based provider; remove it from environment variables",
                    Map.of("name", name));
        }

        OidcProviderConfiguration provider = configurationService.getProviderByName(name)
                .orElseThrow(() -> problem(OIDC_PROVIDER_NOT_FOUND, "Provider not found",
                        Map.of("name", name)));

        Map<String, Object> providerDetails = new HashMap<>();
        providerDetails.put("displayName", provider.getDisplayName());
        providerDetails.put("enabled", provider.isEnabled());
        providerDetails.put("clientId", provider.getClientId());
        providerDetails.put("discoveryUrl", provider.getDiscoveryUrl());

        // Delete from database
        configurationService.deleteProvider(name);

        // Audit log
        String ipAddress = UserIpAddress.resolve(httpRequest);
        auditLogService.logAction(
                adminId,
                ActionType.OIDC_PROVIDER_DELETED,
                TargetType.OIDC_PROVIDER,
                name,
                providerDetails,
                ipAddress
        );

    }

    /**
     * Reset an OIDC provider to environment variable configuration.
     */
    @POST
    @Path("/{name}/reset")
    @RolesAllowed(SecurityRoles.ADMIN)
    public OidcProviderResponse resetProvider(@PathParam("name") String name) {

        UUID adminId = currentUserService.getCurrentUserId();

        // Check if provider exists in environment
        if (!configurationService.isFromEnvironment(name)) {
            throw problem(OIDC_PROVIDER_ENVIRONMENT_MISSING,
                    "Provider does not exist in environment variables", Map.of("name", name));
        }

        configurationService.deleteProvider(name);

        // Audit log
        String ipAddress = UserIpAddress.resolve(httpRequest);
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
                .orElseThrow(() -> problem(OIDC_PROVIDER_NOT_FOUND,
                        "Failed to load environment provider after reset", Map.of("name", name)));

        return mapToResponse(envProvider);
    }

    /**
     * Test connection to an OIDC provider's discovery endpoint.
     */
    @POST
    @Path("/{name}/connection-tests")
    @RolesAllowed(SecurityRoles.ADMIN)
    public TestOidcProviderResponse testProvider(@PathParam("name") String name) {
        try {
            // Get provider configuration
            OidcProviderConfiguration provider = configurationService.getProviderByName(name)
                    .orElseThrow(() -> problem(OIDC_PROVIDER_NOT_FOUND, "Provider not found",
                            Map.of("name", name)));

            // Attempt to fetch discovery document
            OidcDiscoveryDocument discovery = RestClientBuilder.newBuilder()
                    .baseUri(URI.create(provider.getDiscoveryUrl()))
                    .followRedirects(true)
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

            return response;

        } catch (io.quarkiverse.httpproblem.HttpProblem e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to test OIDC provider connection: {}", e.getMessage(), e);
            return TestOidcProviderResponse.failure(e.getClass().getSimpleName(), e.getMessage());
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
