package org.github.tess1o.geopulse.admin.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.dto.AdminSettingsBackupDto;
import org.github.tess1o.geopulse.admin.dto.AdminSettingsImportResult;
import org.github.tess1o.geopulse.admin.dto.UpdateSettingRequest;
import org.github.tess1o.geopulse.admin.model.SettingDefinition;
import org.github.tess1o.geopulse.admin.model.SystemSettingsEntity;
import org.github.tess1o.geopulse.admin.repository.SystemSettingsRepository;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;
import org.github.tess1o.geopulse.geocoding.dto.CustomGeocodingProviderRequest;
import org.github.tess1o.geopulse.geocoding.dto.CustomGeocodingProviderResponse;
import org.github.tess1o.geopulse.geocoding.service.CustomGeocodingProviderService;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class AdminSettingsBackupService {

    private static final Set<String> BACKUP_EXCLUDED_SETTING_KEYS = Set.of(
            "import.drop-folder.path"
    );

    @Inject
    SystemSettingsService settingsService;

    @Inject
    SystemSettingsRepository settingsRepository;

    @Inject
    OidcProviderConfigurationService oidcProviderConfigurationService;

    @Inject
    CustomGeocodingProviderService customGeocodingProviderService;

    @Inject
    GeocodingValidationService geocodingValidationService;

    @Inject
    WeatherValidationService weatherValidationService;

    public AdminSettingsBackupDto exportBackup() {
        Map<String, SettingDefinition> definitions = settingsService.getSettingDefinitions();
        List<AdminSettingsBackupDto.SettingBackupDto> settings = definitions.entrySet().stream()
                .filter(entry -> !BACKUP_EXCLUDED_SETTING_KEYS.contains(entry.getKey()))
                .map(entry -> AdminSettingsBackupDto.SettingBackupDto.builder()
                        .key(entry.getKey())
                        .category(entry.getValue().category())
                        .valueType(entry.getValue().valueType())
                        .value(settingsService.getString(entry.getKey()))
                        .build())
                .toList();

        return AdminSettingsBackupDto.builder()
                .schemaVersion(AdminSettingsBackupDto.CURRENT_SCHEMA_VERSION)
                .scope(AdminSettingsBackupDto.BACKUP_SCOPE)
                .excludedConfigurationSummary(AdminSettingsBackupDto.EXCLUDED_CONFIGURATION_SUMMARY)
                .exportedAt(Instant.now())
                .settings(settings)
                .oidcProviders(oidcProviderConfigurationService.loadAllProviders().stream()
                        .map(this::toOidcBackup)
                        .toList())
                .customGeocodingProviders(customGeocodingProviderService.listForBackupExport().stream()
                        .map(this::toCustomGeocodingBackup)
                        .toList())
                .build();
    }

    public void validateBackupForImport(AdminSettingsBackupDto backup) {
        validateBackupEnvelope(backup);
        Map<String, SettingDefinition> definitions = settingsService.getSettingDefinitions();
        Map<String, AdminSettingsBackupDto.SettingBackupDto> supportedSettings =
                collectSupportedSettings(definitions, backup, null);
        validateCustomGeocodingProviders(backup);
        validateOidcProviders(backup);
        validateWeatherSettingsState(definitions, supportedSettings);
    }

    @Transactional
    public AdminSettingsImportResult importBackup(AdminSettingsBackupDto backup, UUID adminId) {
        validateBackupEnvelope(backup);

        Map<String, SettingDefinition> definitions = settingsService.getSettingDefinitions();
        List<String> unsupportedSettings = new ArrayList<>();
        Map<String, AdminSettingsBackupDto.SettingBackupDto> supportedSettings =
                collectSupportedSettings(definitions, backup, unsupportedSettings);

        int customImported = replaceCustomGeocodingProviders(backup);
        int customRemoved = removeMissingCustomGeocodingProviders(backup);
        validateFinalSettingsState(definitions, supportedSettings);

        int oidcImported = replaceOidcProviders(backup, adminId);
        int oidcRemoved = removeMissingOidcProviders(backup, adminId);
        int envOverridesCreated = createDisabledEnvironmentOidcOverrides(backup, adminId);

        removeMissingSystemSettings(supportedSettings.keySet());
        supportedSettings.values().forEach(setting ->
                settingsService.setValue(setting.getKey(), safeValue(setting.getValue()), adminId));

        return AdminSettingsImportResult.builder()
                .settingsImported(supportedSettings.size())
                .oidcProvidersImported(oidcImported)
                .oidcProvidersRemoved(oidcRemoved)
                .oidcEnvironmentOverridesCreated(envOverridesCreated)
                .customGeocodingProvidersImported(customImported)
                .customGeocodingProvidersRemoved(customRemoved)
                .unsupportedSettings(unsupportedSettings)
                .build();
    }

    private Map<String, AdminSettingsBackupDto.SettingBackupDto> collectSupportedSettings(
            Map<String, SettingDefinition> definitions,
            AdminSettingsBackupDto backup,
            List<String> unsupportedSettings) {
        Map<String, AdminSettingsBackupDto.SettingBackupDto> supportedSettings = new LinkedHashMap<>();
        for (AdminSettingsBackupDto.SettingBackupDto setting : nullToList(backup.getSettings())) {
            if (setting == null || setting.getKey() == null || setting.getKey().isBlank()) {
                throw new IllegalArgumentException("Settings backup contains a setting without a key");
            }
            SettingDefinition definition = definitions.get(setting.getKey());
            if (definition == null || BACKUP_EXCLUDED_SETTING_KEYS.contains(setting.getKey())) {
                if (unsupportedSettings != null) {
                    unsupportedSettings.add(setting.getKey());
                }
                continue;
            }
            if (setting.getValueType() != null && setting.getValueType() != definition.valueType()) {
                throw new IllegalArgumentException("Setting " + setting.getKey() + " has value type "
                        + setting.getValueType() + " but expected " + definition.valueType());
            }
            if (supportedSettings.put(setting.getKey(), setting) != null) {
                throw new IllegalArgumentException("Duplicate setting in backup: " + setting.getKey());
            }
            settingsService.validateValueForImport(setting.getKey(), safeValue(setting.getValue()));
        }
        return supportedSettings;
    }

    private void validateBackupEnvelope(AdminSettingsBackupDto backup) {
        if (backup == null) {
            throw new IllegalArgumentException("Settings backup file is empty");
        }
        if (backup.getSchemaVersion() != AdminSettingsBackupDto.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported admin settings backup schema version: " + backup.getSchemaVersion());
        }
    }

    private void validateCustomGeocodingProviders(AdminSettingsBackupDto backup) {
        Set<String> names = new HashSet<>();
        for (AdminSettingsBackupDto.CustomGeocodingProviderBackupDto provider : nullToList(backup.getCustomGeocodingProviders())) {
            if (provider == null) {
                throw new IllegalArgumentException("Custom geocoding providers backup contains an empty provider");
            }
            requireValue(provider.getName(), "Custom geocoding provider name is required");
            requireValue(provider.getDisplayName(), "Custom geocoding provider display name is required");
            requireValue(provider.getType(), "Custom geocoding provider type is required");
            requireValue(provider.getUrl(), "Custom geocoding provider URL is required");
            String normalizedName = normalizeName(provider.getName());
            if (!names.add(normalizedName)) {
                throw new IllegalArgumentException("Duplicate custom geocoding provider in backup: " + provider.getName());
            }
            if (isBuiltInGeocodingProvider(normalizedName)) {
                throw new IllegalArgumentException("Custom provider name cannot match a built-in provider: " + normalizedName);
            }
            validateHttpUrl(provider.getUrl(), "Provider URL is invalid");
            if (provider.getDelayMs() != null && provider.getDelayMs() < 0) {
                throw new IllegalArgumentException("Delay must be zero or greater");
            }
        }
    }

    private void validateOidcProviders(AdminSettingsBackupDto backup) {
        Set<String> names = new HashSet<>();
        for (AdminSettingsBackupDto.OidcProviderBackupDto provider : nullToList(backup.getOidcProviders())) {
            if (provider == null) {
                throw new IllegalArgumentException("OIDC providers backup contains an empty provider");
            }
            if (!names.add(normalizeName(provider.getName()))) {
                throw new IllegalArgumentException("Duplicate OIDC provider in backup: " + provider.getName());
            }
            validateOidcProvider(provider);
        }
    }

    private void validateFinalSettingsState(Map<String, SettingDefinition> definitions,
                                            Map<String, AdminSettingsBackupDto.SettingBackupDto> supportedSettings) {
        validateGeocodingSettingsState(definitions, supportedSettings);
        validateWeatherSettingsState(definitions, supportedSettings);
    }

    private void validateGeocodingSettingsState(Map<String, SettingDefinition> definitions,
                                                Map<String, AdminSettingsBackupDto.SettingBackupDto> supportedSettings) {
        List<UpdateSettingRequest> finalSettings = definitions.entrySet().stream()
                .map(entry -> toUpdateRequest(
                        entry.getKey(),
                        supportedSettings.containsKey(entry.getKey())
                                ? safeValue(supportedSettings.get(entry.getKey()).getValue())
                                : settingsService.getDefaultValue(entry.getKey())))
                .toList();

        String geocodingError = geocodingValidationService.validateGeocodingChanges(finalSettings.stream()
                .filter(setting -> setting.getKey().startsWith("geocoding."))
                .toList());
        if (geocodingError != null) {
            throw new IllegalArgumentException(geocodingError);
        }
    }

    private void validateWeatherSettingsState(Map<String, SettingDefinition> definitions,
                                              Map<String, AdminSettingsBackupDto.SettingBackupDto> supportedSettings) {
        List<UpdateSettingRequest> finalSettings = definitions.entrySet().stream()
                .map(entry -> toUpdateRequest(
                        entry.getKey(),
                        supportedSettings.containsKey(entry.getKey())
                                ? safeValue(supportedSettings.get(entry.getKey()).getValue())
                                : settingsService.getDefaultValue(entry.getKey())))
                .toList();
        String weatherError = weatherValidationService.validateWeatherChanges(finalSettings.stream()
                .filter(setting -> setting.getKey().startsWith("weather."))
                .toList());
        if (weatherError != null) {
            throw new IllegalArgumentException(weatherError);
        }
    }

    private int replaceCustomGeocodingProviders(AdminSettingsBackupDto backup) {
        int imported = 0;
        Set<String> names = new HashSet<>();
        for (AdminSettingsBackupDto.CustomGeocodingProviderBackupDto provider : nullToList(backup.getCustomGeocodingProviders())) {
            if (!names.add(normalizeName(provider.getName()))) {
                throw new IllegalArgumentException("Duplicate custom geocoding provider in backup: " + provider.getName());
            }
            customGeocodingProviderService.upsertFromBackup(toCustomGeocodingRequest(provider));
            imported++;
        }
        return imported;
    }

    private int removeMissingCustomGeocodingProviders(AdminSettingsBackupDto backup) {
        Set<String> importedNames = nullToList(backup.getCustomGeocodingProviders()).stream()
                .map(provider -> normalizeName(provider.getName()))
                .collect(Collectors.toSet());

        int removed = 0;
        for (CustomGeocodingProviderResponse existing : customGeocodingProviderService.listForBackupExport()) {
            if (!importedNames.contains(normalizeName(existing.getName()))
                    && customGeocodingProviderService.deleteFromBackupImport(existing.getName())) {
                removed++;
            }
        }
        return removed;
    }

    private int replaceOidcProviders(AdminSettingsBackupDto backup, UUID adminId) {
        int imported = 0;
        Set<String> names = new HashSet<>();
        for (AdminSettingsBackupDto.OidcProviderBackupDto provider : nullToList(backup.getOidcProviders())) {
            if (!names.add(normalizeName(provider.getName()))) {
                throw new IllegalArgumentException("Duplicate OIDC provider in backup: " + provider.getName());
            }
            validateOidcProvider(provider);
            oidcProviderConfigurationService.saveProvider(toOidcConfiguration(provider), adminId);
            imported++;
        }
        return imported;
    }

    private int removeMissingOidcProviders(AdminSettingsBackupDto backup, UUID adminId) {
        Set<String> importedNames = importedOidcNames(backup);
        int removed = 0;
        for (OidcProviderConfiguration existing : oidcProviderConfigurationService.loadAllProviders()) {
            if (!importedNames.contains(normalizeName(existing.getName()))
                    && oidcProviderConfigurationService.existsInDatabase(existing.getName())) {
                oidcProviderConfigurationService.deleteProvider(existing.getName());
                removed++;
            }
        }
        return removed;
    }

    private int createDisabledEnvironmentOidcOverrides(AdminSettingsBackupDto backup, UUID adminId) {
        Set<String> importedNames = importedOidcNames(backup);
        int created = 0;
        for (OidcProviderConfiguration existing : oidcProviderConfigurationService.loadAllProviders()) {
            if (!importedNames.contains(normalizeName(existing.getName()))
                    && oidcProviderConfigurationService.isFromEnvironment(existing.getName())
                    && !oidcProviderConfigurationService.existsInDatabase(existing.getName())) {
                oidcProviderConfigurationService.saveProvider(existing.toBuilder()
                        .enabled(false)
                        .metadataValid(false)
                        .metadataCachedAt(null)
                        .build(), adminId);
                created++;
            }
        }
        return created;
    }

    private void removeMissingSystemSettings(Set<String> importedSettingKeys) {
        for (SystemSettingsEntity existing : settingsRepository.listAllSettings()) {
            if (!importedSettingKeys.contains(existing.getKey())) {
                settingsRepository.deleteByKey(existing.getKey());
            }
        }
    }

    private AdminSettingsBackupDto.OidcProviderBackupDto toOidcBackup(OidcProviderConfiguration provider) {
        return AdminSettingsBackupDto.OidcProviderBackupDto.builder()
                .name(provider.getName())
                .displayName(provider.getDisplayName())
                .enabled(provider.isEnabled())
                .clientId(provider.getClientId())
                .clientSecret(provider.getClientSecret())
                .discoveryUrl(provider.getDiscoveryUrl())
                .icon(provider.getIcon())
                .scopes(provider.getScopes())
                .authorizationEndpoint(provider.getAuthorizationEndpoint())
                .tokenEndpoint(provider.getTokenEndpoint())
                .userinfoEndpoint(provider.getUserinfoEndpoint())
                .jwksUri(provider.getJwksUri())
                .issuer(provider.getIssuer())
                .metadataCachedAt(provider.getMetadataCachedAt())
                .metadataValid(provider.isMetadataValid())
                .build();
    }

    private OidcProviderConfiguration toOidcConfiguration(AdminSettingsBackupDto.OidcProviderBackupDto provider) {
        return OidcProviderConfiguration.builder()
                .name(normalizeName(provider.getName()))
                .displayName(provider.getDisplayName().trim())
                .enabled(provider.isEnabled())
                .clientId(provider.getClientId().trim())
                .clientSecret(provider.getClientSecret())
                .discoveryUrl(provider.getDiscoveryUrl().trim())
                .icon(trimToNull(provider.getIcon()))
                .scopes(blank(provider.getScopes()) ? "openid profile email" : provider.getScopes().trim())
                .authorizationEndpoint(trimToNull(provider.getAuthorizationEndpoint()))
                .tokenEndpoint(trimToNull(provider.getTokenEndpoint()))
                .userinfoEndpoint(trimToNull(provider.getUserinfoEndpoint()))
                .jwksUri(trimToNull(provider.getJwksUri()))
                .issuer(trimToNull(provider.getIssuer()))
                .metadataCachedAt(provider.getMetadataCachedAt())
                .metadataValid(provider.isMetadataValid())
                .build();
    }

    private void validateOidcProvider(AdminSettingsBackupDto.OidcProviderBackupDto provider) {
        if (provider == null) {
            throw new IllegalArgumentException("OIDC providers backup contains an empty provider");
        }
        requireValue(provider.getName(), "OIDC provider name is required");
        requireValue(provider.getDisplayName(), "OIDC provider display name is required");
        requireValue(provider.getClientId(), "OIDC provider client ID is required");
        requireValue(provider.getClientSecret(), "OIDC provider client secret is required");
        requireValue(provider.getDiscoveryUrl(), "OIDC provider discovery URL is required");
        if (!normalizeName(provider.getName()).matches("^[a-z0-9-]+$")) {
            throw new IllegalArgumentException("OIDC provider name must be lowercase alphanumeric with hyphens: " + provider.getName());
        }
    }

    private AdminSettingsBackupDto.CustomGeocodingProviderBackupDto toCustomGeocodingBackup(CustomGeocodingProviderResponse provider) {
        return AdminSettingsBackupDto.CustomGeocodingProviderBackupDto.builder()
                .name(provider.getName())
                .displayName(provider.getDisplayName())
                .type(provider.getType())
                .url(provider.getUrl())
                .enabled(provider.getEnabled())
                .language(provider.getLanguage())
                .headers(provider.getHeaders())
                .delayMs(provider.getDelayMs())
                .build();
    }

    private CustomGeocodingProviderRequest toCustomGeocodingRequest(AdminSettingsBackupDto.CustomGeocodingProviderBackupDto provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Custom geocoding providers backup contains an empty provider");
        }
        requireValue(provider.getName(), "Custom geocoding provider name is required");
        requireValue(provider.getDisplayName(), "Custom geocoding provider display name is required");
        requireValue(provider.getType(), "Custom geocoding provider type is required");
        requireValue(provider.getUrl(), "Custom geocoding provider URL is required");
        CustomGeocodingProviderRequest request = new CustomGeocodingProviderRequest();
        request.setName(provider.getName());
        request.setDisplayName(provider.getDisplayName());
        request.setType(provider.getType());
        request.setUrl(provider.getUrl());
        request.setEnabled(Boolean.TRUE.equals(provider.getEnabled()));
        request.setLanguage(provider.getLanguage());
        request.setHeaders(provider.getHeaders());
        request.setDelayMs(provider.getDelayMs());
        return request;
    }

    private UpdateSettingRequest toUpdateRequest(String key, String value) {
        UpdateSettingRequest request = new UpdateSettingRequest();
        request.setKey(key);
        request.setValue(value);
        return request;
    }

    private void validateHttpUrl(String value, String message) {
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(message);
        }
        if (uri.getScheme() == null || uri.getHost() == null
                || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Provider URL must be an http(s) URL");
        }
    }

    private boolean isBuiltInGeocodingProvider(String name) {
        return List.of("nominatim", "photon", "googlemaps", "mapbox", "geoapify", "chibigeo")
                .contains(name);
    }

    private Set<String> importedOidcNames(AdminSettingsBackupDto backup) {
        return nullToList(backup.getOidcProviders()).stream()
                .filter(Objects::nonNull)
                .map(AdminSettingsBackupDto.OidcProviderBackupDto::getName)
                .map(this::normalizeName)
                .collect(Collectors.toSet());
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void requireValue(String value, String message) {
        if (blank(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private <T> List<T> nullToList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
