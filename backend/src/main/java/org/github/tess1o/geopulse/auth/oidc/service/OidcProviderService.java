package org.github.tess1o.geopulse.auth.oidc.service;

import io.quarkus.runtime.annotations.StaticInitSafe;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.github.tess1o.geopulse.admin.service.OidcProviderConfigurationService;
import org.github.tess1o.geopulse.auth.oidc.model.OidcDiscoveryDocument;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class OidcProviderService {

    private final Map<String, Instant> metadataCache = new ConcurrentHashMap<>();

    @Inject
    OidcProviderConfigurationService configurationService;

    @ConfigProperty(name = "geopulse.oidc.metadata-cache.ttl-hours", defaultValue = "24")
    @StaticInitSafe
    int metadataCacheTtlHours;

    /**
     * Get all enabled OIDC providers from DB and environment.
     * Populates metadata from discovery if not cached.
     *
     * @return List of enabled provider configurations
     */
    public List<OidcProviderConfiguration> getEnabledProviders() {
        List<OidcProviderConfiguration> allProviders = configurationService.loadAllProviders();

        return allProviders.stream()
                .filter(OidcProviderConfiguration::isEnabled)
                .map(this::ensureMetadataPopulated)
                .collect(Collectors.toList());
    }

    /**
     * Find an OIDC provider by name from DB or environment.
     * Populates metadata from discovery if not cached.
     *
     * @param name Provider name
     * @return Optional provider configuration
     */
    public Optional<OidcProviderConfiguration> findByName(String name) {
        return configurationService.getProviderByName(name)
                .map(this::ensureMetadataPopulated);
    }

    /**
     * Ensure provider has valid metadata, fetch if needed.
     *
     * @param provider Provider configuration
     * @return Provider with populated metadata
     */
    private OidcProviderConfiguration ensureMetadataPopulated(OidcProviderConfiguration provider) {
        try {
            // Check if metadata needs refresh
            Instant cachedAt = metadataCache.get(provider.getName());
            boolean needsRefresh = cachedAt == null ||
                    cachedAt.plus(metadataCacheTtlHours, ChronoUnit.HOURS).isBefore(Instant.now()) ||
                    !provider.isMetadataValid();

            if (needsRefresh) {
                populateMetadataFromDiscovery(provider);
            }
        } catch (Exception e) {
            log.error("Failed to populate metadata for provider '{}': {}", provider.getName(), e.getMessage());
        }

        return provider;
    }

    /**
     * Fetch and populate metadata from OIDC discovery document.
     * Also persists metadata to database if provider is stored in DB.
     *
     * @param provider Provider configuration
     */
    private void populateMetadataFromDiscovery(OidcProviderConfiguration provider) {
        try {
            log.info("Fetching OIDC metadata for provider: {}", provider.getName());

            // Create REST client for discovery document
            OidcDiscoveryDocument discovery = RestClientBuilder.newBuilder()
                    .baseUri(URI.create(provider.getDiscoveryUrl()))
                    .build(OidcDiscoveryClient.class)
                    .getDiscoveryDocument();

            // Update provider configuration
            provider.setAuthorizationEndpoint(discovery.getAuthorizationEndpoint());
            provider.setTokenEndpoint(discovery.getTokenEndpoint());
            provider.setUserinfoEndpoint(discovery.getUserinfoEndpoint());
            provider.setJwksUri(discovery.getJwksUri());
            provider.setIssuer(discovery.getIssuer());
            provider.setMetadataCachedAt(Instant.now());
            provider.setMetadataValid(true);

            // Cache the metadata timestamp in memory
            metadataCache.put(provider.getName(), Instant.now());

            // If provider is in database, persist metadata
            if (configurationService.existsInDatabase(provider.getName())) {
                configurationService.updateMetadata(provider.getName(), provider);
            }

            log.info("Successfully cached OIDC metadata for provider: {}", provider.getName());

        } catch (Exception e) {
            log.error("Failed to fetch OIDC metadata for provider: {}", provider.getName(), e);
            provider.setMetadataValid(false);

            // Invalidate metadata in DB if provider is in database
            if (configurationService.existsInDatabase(provider.getName())) {
                configurationService.invalidateMetadata(provider.getName());
            }

            throw new RuntimeException("Failed to fetch OIDC provider metadata: " + provider.getName(), e);
        }
    }

    // REST client interface for OIDC discovery
    @Path("/")
    public interface OidcDiscoveryClient {
        @GET
        @Produces(MediaType.APPLICATION_JSON)
        OidcDiscoveryDocument getDiscoveryDocument();
    }
}