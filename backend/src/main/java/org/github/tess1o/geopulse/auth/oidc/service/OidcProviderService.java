package org.github.tess1o.geopulse.auth.oidc.service;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
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

@Startup
@ApplicationScoped
@Slf4j
public class OidcProviderService {
    
    private final Map<String, OidcProviderConfiguration> providers = new ConcurrentHashMap<>();
    private final Map<String, Instant> metadataCache = new ConcurrentHashMap<>();
    
    @ConfigProperty(name = "geopulse.oidc.metadata-cache.ttl-hours", defaultValue = "24")
    int metadataCacheTtlHours;
    
    @PostConstruct
    public void initializeProviders() {
        log.info("Initializing OIDC providers from environment variables");
        
        // Scan environment variables for provider configurations
        Map<String, Map<String, String>> providerConfigs = scanProviderConfigurations();
        
        for (Map.Entry<String, Map<String, String>> entry : providerConfigs.entrySet()) {
            String providerKey = entry.getKey();
            Map<String, String> config = entry.getValue();
            
            if (!"true".equalsIgnoreCase(config.get("enabled"))) {
                log.debug("Skipping disabled provider: {}", providerKey);
                continue;
            }
            
            String clientId = config.get("client-id");
            String clientSecret = config.get("client-secret");
            String discoveryUrl = config.get("discovery-url");
            String displayName = config.getOrDefault("name", providerKey);
            String icon = config.get("icon");
            
            if (clientId == null || clientSecret == null || discoveryUrl == null) {
                log.warn("Skipping provider '{}' due to missing required configuration (client-id, client-secret, or discovery-url)", providerKey);
                continue;
            }
            
            try {
                OidcProviderConfiguration provider = OidcProviderConfiguration.builder()
                        .name(providerKey)
                        .displayName(displayName)
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .discoveryUrl(discoveryUrl)
                        .icon(icon)
                        .enabled(true)
                        .build();
                
                // Populate metadata from discovery URL
                populateMetadataFromDiscovery(provider);
                providers.put(providerKey, provider);
                log.info("OIDC provider '{}' ({}) initialized successfully", providerKey, displayName);
                
            } catch (Exception e) {
                log.error("Failed to initialize OIDC provider '{}': {}", providerKey, e.getMessage());
            }
        }
        
        log.info("Initialized {} OIDC providers", providers.size());
    }
    
    private Map<String, Map<String, String>> scanProviderConfigurations() {
        Map<String, Map<String, String>> providerConfigs = new ConcurrentHashMap<>();
        Config config = ConfigProvider.getConfig();
        
        // First, scan application properties with pattern: geopulse.oidc.provider.{name}.{property}
        String propertyPrefix = "geopulse.oidc.provider.";
        for (String propertyName : config.getPropertyNames()) {
            if (propertyName.startsWith(propertyPrefix)) {
                String remaining = propertyName.substring(propertyPrefix.length());
                int firstDot = remaining.indexOf('.');
                
                if (firstDot > 0) {
                    String providerName = remaining.substring(0, firstDot).toLowerCase();
                    String configKey = remaining.substring(firstDot + 1).toLowerCase();
                    String value = config.getValue(propertyName, String.class);
                    
                    if (value != null && !value.trim().isEmpty()) {
                        providerConfigs.computeIfAbsent(providerName, ignored -> new ConcurrentHashMap<>())
                                      .put(configKey, value);
                    }
                }
            }
        }
        
        // Then, scan environment variables with pattern: GEOPULSE_OIDC_PROVIDER_{NAME}_{PROPERTY}
        // Environment variables take precedence over application properties
        String envPrefix = "GEOPULSE_OIDC_PROVIDER_";
        for (Map.Entry<String, String> envEntry : System.getenv().entrySet()) {
            String envKey = envEntry.getKey();
            String envValue = envEntry.getValue();
            
            if (envKey.startsWith(envPrefix)) {
                String remaining = envKey.substring(envPrefix.length());
                int firstUnderscore = remaining.indexOf('_');
                
                if (firstUnderscore > 0) {
                    String providerName = remaining.substring(0, firstUnderscore).toLowerCase();
                    String configKey = remaining.substring(firstUnderscore + 1).toLowerCase().replace('_', '-');
                    
                    if (envValue != null && !envValue.trim().isEmpty()) {
                        providerConfigs.computeIfAbsent(providerName, ignored -> new ConcurrentHashMap<>())
                                      .put(configKey, envValue);
                    }
                }
            }
        }
        
        log.info("Found {} provider configurations from properties and environment variables", providerConfigs.size());

        return providerConfigs;
    }
    
    public List<OidcProviderConfiguration> getEnabledProviders() {
        return providers.values().stream()
                .filter(OidcProviderConfiguration::isEnabled)
                .collect(Collectors.toList());
    }
    
    public Optional<OidcProviderConfiguration> findByName(String name) {
        return Optional.ofNullable(providers.get(name));
    }
    
    private void populateMetadataFromDiscovery(OidcProviderConfiguration provider) {
        try {
            // Check if metadata needs refresh
            Instant cachedAt = metadataCache.get(provider.getName());
            if (cachedAt != null && cachedAt.plus(metadataCacheTtlHours, ChronoUnit.HOURS).isAfter(Instant.now())) {
                return; // Metadata is still valid
            }
            
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
            
            // Cache the metadata timestamp
            metadataCache.put(provider.getName(), Instant.now());
            
            log.info("Successfully cached OIDC metadata for provider: {}", provider.getName());
            
        } catch (Exception e) {
            log.error("Failed to fetch OIDC metadata for provider: {}", provider.getName(), e);
            provider.setMetadataValid(false);
            throw new RuntimeException("Failed to initialize OIDC provider: " + provider.getName(), e);
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