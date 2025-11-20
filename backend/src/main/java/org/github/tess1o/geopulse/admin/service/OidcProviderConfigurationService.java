package org.github.tess1o.geopulse.admin.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.github.tess1o.geopulse.admin.model.OidcProviderEntity;
import org.github.tess1o.geopulse.admin.repository.OidcProviderRepository;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@Slf4j
public class OidcProviderConfigurationService {

    @Inject
    OidcProviderRepository repository;

    @Inject
    AIEncryptionService encryptionService;

    /**
     * Load all OIDC providers from DB and environment variables.
     * DB providers override environment providers with the same name.
     *
     * @return List of all available provider configurations
     */
    public List<OidcProviderConfiguration> loadAllProviders() {
        Map<String, OidcProviderConfiguration> allProviders = new LinkedHashMap<>();

        // 1. Load from environment variables first (as defaults)
        Map<String, Map<String, String>> envProviders = scanProviderConfigurations();
        for (Map.Entry<String, Map<String, String>> entry : envProviders.entrySet()) {
            String providerName = entry.getKey();
            Map<String, String> config = entry.getValue();

            OidcProviderConfiguration provider = buildProviderFromEnvConfig(providerName, config);
            if (provider != null) {
                allProviders.put(providerName, provider);
            }
        }

        // 2. Load from database (overrides environment)
        List<OidcProviderEntity> dbProviders = repository.listAll();
        for (OidcProviderEntity entity : dbProviders) {
            try {
                OidcProviderConfiguration provider = mapEntityToConfiguration(entity);
                allProviders.put(entity.getName(), provider); // Override env provider if exists
            } catch (Exception e) {
                log.error("Failed to load provider '{}' from database: {}", entity.getName(), e.getMessage(), e);
            }
        }

        return new ArrayList<>(allProviders.values());
    }

    /**
     * Get a single provider by name. Checks DB first, then falls back to environment variables.
     *
     * @param name Provider name
     * @return Optional provider configuration
     */
    public Optional<OidcProviderConfiguration> getProviderByName(String name) {
        // 1. Check database first
        Optional<OidcProviderEntity> dbProvider = repository.findByName(name);
        if (dbProvider.isPresent()) {
            try {
                return Optional.of(mapEntityToConfiguration(dbProvider.get()));
            } catch (Exception e) {
                log.error("Failed to load provider '{}' from database: {}", name, e.getMessage(), e);
                return Optional.empty();
            }
        }

        // 2. Fall back to environment variables
        Map<String, Map<String, String>> envProviders = scanProviderConfigurations();
        if (envProviders.containsKey(name)) {
            OidcProviderConfiguration provider = buildProviderFromEnvConfig(name, envProviders.get(name));
            return Optional.ofNullable(provider);
        }

        return Optional.empty();
    }

    /**
     * Save or update an OIDC provider. Encrypts the client secret before storing.
     *
     * @param provider Provider configuration to save
     * @param adminUserId Admin user performing the operation
     * @return Saved provider configuration
     */
    @Transactional
    public OidcProviderConfiguration saveProvider(OidcProviderConfiguration provider, UUID adminUserId) {
        Optional<OidcProviderEntity> existingOpt = repository.findByName(provider.getName());

        OidcProviderEntity entity;
        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            entity.setUpdatedAt(Instant.now());
            entity.setUpdatedBy(adminUserId);
        } else {
            entity = new OidcProviderEntity();
            entity.setName(provider.getName());
            entity.setCreatedAt(Instant.now());
            entity.setCreatedBy(adminUserId);
        }

        // Encrypt client secret
        String encryptedSecret = encryptionService.encrypt(provider.getClientSecret());
        String keyId = encryptionService.getCurrentKeyId();

        entity.setDisplayName(provider.getDisplayName());
        entity.setEnabled(provider.isEnabled());
        entity.setClientId(provider.getClientId());
        entity.setClientSecretEncrypted(encryptedSecret);
        entity.setClientSecretKeyId(keyId);
        entity.setDiscoveryUrl(provider.getDiscoveryUrl());
        entity.setIcon(provider.getIcon());
        entity.setScopes(provider.getScopes());

        // Copy cached metadata if present
        entity.setAuthorizationEndpoint(provider.getAuthorizationEndpoint());
        entity.setTokenEndpoint(provider.getTokenEndpoint());
        entity.setUserinfoEndpoint(provider.getUserinfoEndpoint());
        entity.setJwksUri(provider.getJwksUri());
        entity.setIssuer(provider.getIssuer());
        entity.setMetadataCachedAt(provider.getMetadataCachedAt());
        entity.setMetadataValid(provider.isMetadataValid());

        repository.persist(entity);

        log.info("Saved OIDC provider '{}' to database", provider.getName());

        return mapEntityToConfiguration(entity);
    }

    /**
     * Update only the client secret for an existing provider.
     *
     * @param name Provider name
     * @param newClientSecret New client secret
     * @param adminUserId Admin user performing the operation
     */
    @Transactional
    public void updateClientSecret(String name, String newClientSecret, UUID adminUserId) {
        OidcProviderEntity entity = repository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + name));

        String encryptedSecret = encryptionService.encrypt(newClientSecret);
        String keyId = encryptionService.getCurrentKeyId();

        entity.setClientSecretEncrypted(encryptedSecret);
        entity.setClientSecretKeyId(keyId);
        entity.setUpdatedAt(Instant.now());
        entity.setUpdatedBy(adminUserId);

        repository.persist(entity);

        log.info("Updated client secret for OIDC provider '{}'", name);
    }

    /**
     * Delete a provider from the database. If the provider exists in environment variables,
     * it will revert to the env var configuration.
     *
     * @param name Provider name to delete
     */
    @Transactional
    public void deleteProvider(String name) {
        repository.deleteByName(name);
        log.info("Deleted OIDC provider '{}' from database", name);
    }

    /**
     * Check if a provider is defined in environment variables.
     *
     * @param name Provider name
     * @return true if provider is defined in env vars
     */
    public boolean isFromEnvironment(String name) {
        Map<String, Map<String, String>> envProviders = scanProviderConfigurations();
        return envProviders.containsKey(name);
    }

    /**
     * Check if a provider exists in the database.
     *
     * @param name Provider name
     * @return true if provider exists in database
     */
    public boolean existsInDatabase(String name) {
        return repository.existsByName(name);
    }

    /**
     * Invalidate cached metadata for a provider.
     *
     * @param name Provider name
     */
    @Transactional
    public void invalidateMetadata(String name) {
        Optional<OidcProviderEntity> entityOpt = repository.findByName(name);
        if (entityOpt.isPresent()) {
            OidcProviderEntity entity = entityOpt.get();
            entity.setMetadataValid(false);
            entity.setMetadataCachedAt(null);
            repository.persist(entity);
            log.info("Invalidated metadata cache for OIDC provider '{}'", name);
        }
    }

    /**
     * Update cached metadata for a provider.
     *
     * @param name Provider name
     * @param provider Provider configuration with updated metadata
     */
    @Transactional
    public void updateMetadata(String name, OidcProviderConfiguration provider) {
        Optional<OidcProviderEntity> entityOpt = repository.findByName(name);
        if (entityOpt.isPresent()) {
            OidcProviderEntity entity = entityOpt.get();
            entity.setAuthorizationEndpoint(provider.getAuthorizationEndpoint());
            entity.setTokenEndpoint(provider.getTokenEndpoint());
            entity.setUserinfoEndpoint(provider.getUserinfoEndpoint());
            entity.setJwksUri(provider.getJwksUri());
            entity.setIssuer(provider.getIssuer());
            entity.setMetadataCachedAt(provider.getMetadataCachedAt());
            entity.setMetadataValid(provider.isMetadataValid());
            repository.persist(entity);
            log.debug("Updated metadata cache for OIDC provider '{}'", name);
        }
    }

    /**
     * Map database entity to configuration object.
     */
    private OidcProviderConfiguration mapEntityToConfiguration(OidcProviderEntity entity) {
        // Decrypt client secret
        String decryptedSecret = encryptionService.decrypt(
                entity.getClientSecretEncrypted(),
                entity.getClientSecretKeyId()
        );

        return OidcProviderConfiguration.builder()
                .name(entity.getName())
                .displayName(entity.getDisplayName())
                .clientId(entity.getClientId())
                .clientSecret(decryptedSecret)
                .discoveryUrl(entity.getDiscoveryUrl())
                .icon(entity.getIcon())
                .scopes(entity.getScopes())
                .enabled(entity.getEnabled())
                .authorizationEndpoint(entity.getAuthorizationEndpoint())
                .tokenEndpoint(entity.getTokenEndpoint())
                .userinfoEndpoint(entity.getUserinfoEndpoint())
                .jwksUri(entity.getJwksUri())
                .issuer(entity.getIssuer())
                .metadataCachedAt(entity.getMetadataCachedAt())
                .metadataValid(entity.getMetadataValid())
                .build();
    }

    /**
     * Build provider configuration from environment variable config map.
     */
    private OidcProviderConfiguration buildProviderFromEnvConfig(String name, Map<String, String> config) {
        if (!"true".equalsIgnoreCase(config.get("enabled"))) {
            return null;
        }

        String clientId = config.get("client-id");
        String clientSecret = config.get("client-secret");
        String discoveryUrl = config.get("discovery-url");

        if (clientId == null || clientSecret == null || discoveryUrl == null) {
            log.warn("Skipping provider '{}' from environment due to missing required configuration", name);
            return null;
        }

        return OidcProviderConfiguration.builder()
                .name(name)
                .displayName(config.getOrDefault("name", name))
                .clientId(clientId)
                .clientSecret(clientSecret)
                .discoveryUrl(discoveryUrl)
                .icon(config.get("icon"))
                .scopes(config.getOrDefault("scopes", "openid profile email"))
                .enabled(true)
                .metadataValid(false)
                .build();
    }

    /**
     * Scan environment variables and application properties for OIDC provider configurations.
     * Pattern: GEOPULSE_OIDC_PROVIDER_{NAME}_{PROPERTY} or geopulse.oidc.provider.{name}.{property}
     *
     * @return Map of provider name to configuration properties
     */
    private Map<String, Map<String, String>> scanProviderConfigurations() {
        Map<String, Map<String, String>> providerConfigs = new ConcurrentHashMap<>();
        Config config = ConfigProvider.getConfig();

        // 1. Scan application properties: geopulse.oidc.provider.{name}.{property}
        String propertyPrefix = "geopulse.oidc.provider.";
        for (String propertyName : config.getPropertyNames()) {
            if (propertyName.startsWith(propertyPrefix)) {
                String remaining = propertyName.substring(propertyPrefix.length());
                int firstDot = remaining.indexOf('.');

                if (firstDot > 0) {
                    String providerName = remaining.substring(0, firstDot).toLowerCase();
                    String configKey = remaining.substring(firstDot + 1).toLowerCase();

                    Optional<String> optionalValue = config.getOptionalValue(propertyName, String.class);
                    if (optionalValue.isPresent()) {
                        String value = optionalValue.get();
                        if (value != null && !value.trim().isEmpty()) {
                            providerConfigs.computeIfAbsent(providerName, ignored -> new ConcurrentHashMap<>())
                                    .put(configKey, value);
                        }
                    }
                }
            }
        }

        // 2. Scan environment variables: GEOPULSE_OIDC_PROVIDER_{NAME}_{PROPERTY}
        // Environment variables override application properties
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

        return providerConfigs;
    }
}
