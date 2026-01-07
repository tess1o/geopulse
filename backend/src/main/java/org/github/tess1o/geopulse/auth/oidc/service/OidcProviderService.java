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

    // Cache for metadata timestamps (to check TTL)
    private final Map<String, Instant> metadataCache = new ConcurrentHashMap<>();

    // Cache for actual discovered metadata (for env-configured providers)
    private record CachedMetadata(String authorizationEndpoint, String tokenEndpoint,
                                   String userinfoEndpoint, String jwksUri, String issuer) {}
    private final Map<String, CachedMetadata> discoveryCache = new ConcurrentHashMap<>();

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
        // If provider object already has endpoints, no need to fetch
        if (provider.getAuthorizationEndpoint() != null && provider.getTokenEndpoint() != null) {
            return provider;
        }

        // Provider needs metadata - check if we have it cached
        Instant cachedAt = metadataCache.get(provider.getName());
        boolean cacheExpired = cachedAt == null ||
                cachedAt.plus(metadataCacheTtlHours, ChronoUnit.HOURS).isBefore(Instant.now());

        if (cacheExpired) {
            // Cache expired or missing - fetch from discovery
            synchronized (metadataCache) {
                // Double-check after acquiring lock
                Instant recheckCachedAt = metadataCache.get(provider.getName());
                if (recheckCachedAt == null || recheckCachedAt.plus(metadataCacheTtlHours, ChronoUnit.HOURS).isBefore(Instant.now())) {
                    populateMetadataFromDiscovery(provider);
                } else {
                    // Another thread just populated it - load from cache
                    loadCachedMetadata(provider);
                }
            }
        } else {
            // Cache is fresh - load metadata from cache instead of refetching
            loadCachedMetadata(provider);
        }

        return provider;
    }

    private void loadCachedMetadata(OidcProviderConfiguration provider) {
        // Try in-memory discovery cache first (for env-configured providers)
        CachedMetadata cached = discoveryCache.get(provider.getName());
        if (cached != null) {
            provider.setAuthorizationEndpoint(cached.authorizationEndpoint());
            provider.setTokenEndpoint(cached.tokenEndpoint());
            provider.setUserinfoEndpoint(cached.userinfoEndpoint());
            provider.setJwksUri(cached.jwksUri());
            provider.setIssuer(cached.issuer());
            provider.setMetadataValid(true);
            log.debug("Loaded cached metadata from memory for provider: {}", provider.getName());
            return;
        }

        // Try database (for DB-configured providers)
        if (configurationService.existsInDatabase(provider.getName())) {
            configurationService.getProviderByName(provider.getName()).ifPresent(dbProvider -> {
                if (dbProvider.getAuthorizationEndpoint() != null) {
                    provider.setAuthorizationEndpoint(dbProvider.getAuthorizationEndpoint());
                    provider.setTokenEndpoint(dbProvider.getTokenEndpoint());
                    provider.setUserinfoEndpoint(dbProvider.getUserinfoEndpoint());
                    provider.setJwksUri(dbProvider.getJwksUri());
                    provider.setIssuer(dbProvider.getIssuer());
                    provider.setMetadataValid(true);
                    log.debug("Loaded cached metadata from database for provider: {}", provider.getName());
                }
            });
        }

        // Defensive: If both cache sources failed but timestamp is valid, refetch
        // This shouldn't happen in normal operation but prevents null endpoints
        if (provider.getAuthorizationEndpoint() == null) {
            log.warn("Cache valid but metadata unavailable for provider: {}. Re-synchronizing cache.",
                    provider.getName());
            synchronized (metadataCache) {
                // Re-check inside lock to avoid duplicate fetches
                CachedMetadata recheck = discoveryCache.get(provider.getName());
                if (recheck != null) {
                    provider.setAuthorizationEndpoint(recheck.authorizationEndpoint());
                    provider.setTokenEndpoint(recheck.tokenEndpoint());
                    provider.setUserinfoEndpoint(recheck.userinfoEndpoint());
                    provider.setJwksUri(recheck.jwksUri());
                    provider.setIssuer(recheck.issuer());
                    provider.setMetadataValid(true);
                } else {
                    populateMetadataFromDiscovery(provider);
                }
            }
        }
    }

    /**
     * Fetch and populate metadata from OIDC discovery document.
     * Also persists metadata to database if provider is stored in DB.
     *
     * @param provider Provider configuration
     */
    private void populateMetadataFromDiscovery(OidcProviderConfiguration provider) {
        try {
            log.debug("Fetching OIDC metadata for provider: {}", provider.getName());

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

            // Cache the metadata in memory (for fast reuse)
            discoveryCache.put(provider.getName(), new CachedMetadata(
                    discovery.getAuthorizationEndpoint(),
                    discovery.getTokenEndpoint(),
                    discovery.getUserinfoEndpoint(),
                    discovery.getJwksUri(),
                    discovery.getIssuer()
            ));
            metadataCache.put(provider.getName(), Instant.now());

            // If provider is in database, persist metadata
            if (configurationService.existsInDatabase(provider.getName())) {
                configurationService.updateMetadata(provider.getName(), provider);
            }

            log.debug("Successfully cached OIDC metadata for provider: {}", provider.getName());

        } catch (Exception e) {
            log.error("Failed to fetch OIDC metadata for provider: {}", provider.getName(), e);
            provider.setMetadataValid(false);

            // Invalidate cached metadata
            metadataCache.remove(provider.getName());
            discoveryCache.remove(provider.getName());

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