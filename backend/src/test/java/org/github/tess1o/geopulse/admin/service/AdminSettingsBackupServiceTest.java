package org.github.tess1o.geopulse.admin.service;

import org.github.tess1o.geopulse.admin.dto.AdminSettingsBackupDto;
import org.github.tess1o.geopulse.admin.dto.AdminSettingsImportResult;
import org.github.tess1o.geopulse.admin.dto.UpdateSettingRequest;
import org.github.tess1o.geopulse.admin.model.SettingDefinition;
import org.github.tess1o.geopulse.admin.model.SystemSettingsEntity;
import org.github.tess1o.geopulse.admin.model.ValueType;
import org.github.tess1o.geopulse.admin.repository.SystemSettingsRepository;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;
import org.github.tess1o.geopulse.geocoding.dto.CustomGeocodingProviderResponse;
import org.github.tess1o.geopulse.geocoding.service.CustomGeocodingProviderService;
import org.github.tess1o.geopulse.mapmatching.event.MapMatchingSettingsChangedEvent;
import org.github.tess1o.geopulse.weather.event.WeatherSettingsChangedEvent;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class AdminSettingsBackupServiceTest {

    @Mock
    SystemSettingsService settingsService;
    @Mock
    SystemSettingsRepository settingsRepository;
    @Mock
    OidcProviderConfigurationService oidcProviderConfigurationService;
    @Mock
    CustomGeocodingProviderService customGeocodingProviderService;
    @Mock
    GeocodingValidationService geocodingValidationService;
    @Mock
    WeatherValidationService weatherValidationService;
    @Mock
    AIEncryptionService encryptionService;
    @Mock
    Event<WeatherSettingsChangedEvent> weatherSettingsChangedEvent;
    @Mock
    Event<MapMatchingSettingsChangedEvent> mapMatchingSettingsChangedEvent;

    AdminSettingsBackupService service;
    UUID adminId;

    @BeforeEach
    void setUp() {
        service = new AdminSettingsBackupService();
        service.settingsService = settingsService;
        service.settingsRepository = settingsRepository;
        service.oidcProviderConfigurationService = oidcProviderConfigurationService;
        service.customGeocodingProviderService = customGeocodingProviderService;
        service.geocodingValidationService = geocodingValidationService;
        service.weatherValidationService = weatherValidationService;
        adminId = UUID.randomUUID();
    }

    @Test
    void exportBackupIncludesAllCategoriesAndPlaintextSecrets() {
        Map<String, SettingDefinition> definitions = definitions();
        when(settingsService.getSettingDefinitions()).thenReturn(definitions);
        when(settingsService.getString("auth.registration.enabled")).thenReturn("true");
        when(settingsService.getString("ai.default-system-message")).thenReturn("hello");
        when(settingsService.getString("geocoding.googlemaps.api-key")).thenReturn("plain-google-key");
        when(settingsService.getString("weather.enabled")).thenReturn("true");
        when(settingsService.getString("auth.oidc.callback-base-url")).thenReturn("https://app.example");
        when(settingsService.getString("import.geonames.cities.url")).thenReturn("https://download.example/cities.zip");

        when(oidcProviderConfigurationService.loadAllProviders()).thenReturn(List.of(OidcProviderConfiguration.builder()
                .name("keycloak")
                .displayName("Keycloak")
                .enabled(true)
                .clientId("client-id")
                .clientSecret("plain-oidc-secret")
                .discoveryUrl("https://idp.example/.well-known/openid-configuration")
                .scopes("openid profile email")
                .build()));
        when(customGeocodingProviderService.listForBackupExport()).thenReturn(List.of(CustomGeocodingProviderResponse.builder()
                .name("local-photon")
                .displayName("Local Photon")
                .type("photon")
                .url("https://photon.example")
                .enabled(true)
                .headers(Map.of("X-Api-Key", "plain-header-secret"))
                .build()));

        AdminSettingsBackupDto backup = service.exportBackup();

        assertThat(backup.getScope()).isEqualTo(AdminSettingsBackupDto.BACKUP_SCOPE);
        assertThat(backup.getExcludedConfigurationSummary()).contains("Deployment/runtime infrastructure");
        assertThat(backup.getSettings()).extracting(AdminSettingsBackupDto.SettingBackupDto::getCategory)
                .contains("auth", "ai", "geocoding", "weather", "import");
        assertThat(backup.getSettings()).anySatisfy(setting -> {
            assertThat(setting.getKey()).isEqualTo("auth.oidc.callback-base-url");
            assertThat(setting.getValue()).isEqualTo("https://app.example");
        });
        assertThat(backup.getSettings()).anySatisfy(setting -> {
            assertThat(setting.getKey()).isEqualTo("import.geonames.cities.url");
            assertThat(setting.getValue()).isEqualTo("https://download.example/cities.zip");
        });
        assertThat(backup.getSettings())
                .extracting(AdminSettingsBackupDto.SettingBackupDto::getKey)
                .doesNotContain("import.drop-folder.path");
        assertThat(backup.getSettings()).anySatisfy(setting -> {
            assertThat(setting.getKey()).isEqualTo("geocoding.googlemaps.api-key");
            assertThat(setting.getValue()).isEqualTo("plain-google-key");
        });
        assertThat(backup.getOidcProviders().getFirst().getClientSecret()).isEqualTo("plain-oidc-secret");
        assertThat(backup.getCustomGeocodingProviders().getFirst().getHeaders())
                .containsEntry("X-Api-Key", "plain-header-secret");
    }

    @Test
    void importBackupReplacesSettingsAndProviders() {
        Map<String, SettingDefinition> definitions = definitions();
        when(settingsService.getSettingDefinitions()).thenReturn(definitions);
        when(settingsRepository.listAllSettings()).thenReturn(List.of(SystemSettingsEntity.builder()
                .key("old.setting")
                .build()));
        when(customGeocodingProviderService.listForBackupExport()).thenReturn(List.of(
                CustomGeocodingProviderResponse.builder().name("local-photon").build(),
                CustomGeocodingProviderResponse.builder().name("old-provider").build()));
        when(customGeocodingProviderService.deleteFromBackupImport("old-provider")).thenReturn(true);
        when(oidcProviderConfigurationService.loadAllProviders()).thenReturn(List.of(
                OidcProviderConfiguration.builder().name("keycloak").clientSecret("secret").build(),
                OidcProviderConfiguration.builder().name("old-oidc").clientSecret("old").build()));
        when(oidcProviderConfigurationService.existsInDatabase("old-oidc")).thenReturn(true);
        when(oidcProviderConfigurationService.isFromEnvironment("old-oidc")).thenReturn(false);

        AdminSettingsImportResult result = service.importBackup(backup(), adminId);

        assertThat(result.getSettingsImported()).isEqualTo(4);
        assertThat(result.getOidcProvidersImported()).isEqualTo(1);
        assertThat(result.getOidcProvidersRemoved()).isEqualTo(1);
        assertThat(result.getCustomGeocodingProvidersImported()).isEqualTo(1);
        assertThat(result.getCustomGeocodingProvidersRemoved()).isEqualTo(1);
        verify(customGeocodingProviderService).upsertFromBackup(any());
        verify(customGeocodingProviderService).deleteFromBackupImport("old-provider");
        verify(oidcProviderConfigurationService).saveProvider(any(OidcProviderConfiguration.class), eq(adminId));
        verify(oidcProviderConfigurationService).deleteProvider("old-oidc");
        verify(settingsRepository).deleteByKey("old.setting");
        verify(settingsService).setValue("ai.default-system-message", "hello", adminId);
    }

    @Test
    void importBackupRejectsInvalidSettingBeforeApplyingChanges() {
        Map<String, SettingDefinition> definitions = definitions();
        when(settingsService.getSettingDefinitions()).thenReturn(definitions);
        AdminSettingsBackupDto backup = backup();
        backup.getSettings().stream()
                .filter(setting -> setting.getKey().equals("weather.enabled"))
                .findFirst()
                .orElseThrow()
                .setValue("sometimes");
        doAnswer(invocation -> {
            if ("weather.enabled".equals(invocation.getArgument(0))
                    && "sometimes".equals(invocation.getArgument(1))) {
                throw new IllegalArgumentException("Invalid boolean value: sometimes");
            }
            return null;
        }).when(settingsService).validateValueForImport(any(), any());

        assertThatThrownBy(() -> service.importBackup(backup, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid boolean value: sometimes");

        verifyNoInteractions(customGeocodingProviderService, oidcProviderConfigurationService, settingsRepository);
        verify(settingsService, never()).setValue(any(), any(), any());
    }

    @Test
    void importBackupRejectsCrossFieldValidationBeforePersistingSettings() {
        Map<String, SettingDefinition> definitions = definitions();
        when(settingsService.getSettingDefinitions()).thenReturn(definitions);
        when(customGeocodingProviderService.listForBackupExport()).thenReturn(List.of());
        when(geocodingValidationService.validateGeocodingChanges(anyList()))
                .thenReturn("Cannot disable the last enabled geocoding provider");

        assertThatThrownBy(() -> service.importBackup(backup(), adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot disable the last enabled geocoding provider");

        verify(settingsService, never()).setValue(any(), any(), any());
        verifyNoInteractions(oidcProviderConfigurationService);
    }

    @Test
    void importBackupSkipsUnsupportedSettings() {
        Map<String, SettingDefinition> definitions = definitions();
        when(settingsService.getSettingDefinitions()).thenReturn(definitions);
        when(settingsRepository.listAllSettings()).thenReturn(List.of());
        when(customGeocodingProviderService.listForBackupExport()).thenReturn(List.of());
        when(oidcProviderConfigurationService.loadAllProviders()).thenReturn(List.of());

        AdminSettingsBackupDto backup = backup();
        backup.getSettings().add(AdminSettingsBackupDto.SettingBackupDto.builder()
                .key("future.setting")
                .category("future")
                .valueType(ValueType.STRING)
                .value("value")
                .build());

        AdminSettingsImportResult result = service.importBackup(backup, adminId);

        assertThat(result.getUnsupportedSettings()).containsExactly("future.setting");
        verify(settingsService, never()).setValue(eq("future.setting"), any(), any());
    }

    @Test
    void importBackupSkipsExplicitlyExcludedDeploymentSettings() {
        Map<String, SettingDefinition> definitions = definitions();
        when(settingsService.getSettingDefinitions()).thenReturn(definitions);
        when(settingsRepository.listAllSettings()).thenReturn(List.of());
        when(customGeocodingProviderService.listForBackupExport()).thenReturn(List.of());
        when(oidcProviderConfigurationService.loadAllProviders()).thenReturn(List.of());

        AdminSettingsBackupDto backup = backup();
        backup.getSettings().add(AdminSettingsBackupDto.SettingBackupDto.builder()
                .key("import.drop-folder.path")
                .category("import")
                .valueType(ValueType.STRING)
                .value("/host/path")
                .build());

        AdminSettingsImportResult result = service.importBackup(backup, adminId);

        assertThat(result.getUnsupportedSettings()).containsExactly("import.drop-folder.path");
        verify(settingsService, never()).setValue(eq("import.drop-folder.path"), any(), any());
    }

    @Test
    void settingRegistryPromotesPortableSettingsAndExcludesLegacyOidcEnabledSwitch() {
        SystemSettingsService realSettingsService = new SystemSettingsService(
                settingsRepository,
                encryptionService,
                weatherSettingsChangedEvent,
                mapMatchingSettingsChangedEvent);

        Map<String, SettingDefinition> definitions = realSettingsService.getSettingDefinitions();

        assertThat(definitions).containsKeys(
                "auth.oidc.callback-base-url",
                "auth.oidc.jwks-cache.ttl-hours",
                "auth.oidc.cleanup.session-states.enabled",
                "geocoding.cache.max-bbox-area-km2",
                "geocoding.reconcile.item.max-attempts",
                "weather.open-meteo.connect-timeout-seconds",
                "import.geonames.cities.url",
                "import.geonames.countries.url",
                "import.transaction-timeout-minutes",
                "system.water-dataset.url",
                "system.version-check.github-api-url");
        assertThat(definitions).doesNotContainKey("auth.oidc.enabled");
        assertThat(definitions.get("geocoding.mapbox.access-token").envVarName())
                .isEqualTo("geocoding.mapbox.access-token");
    }

    private AdminSettingsBackupDto backup() {
        return AdminSettingsBackupDto.builder()
                .schemaVersion(AdminSettingsBackupDto.CURRENT_SCHEMA_VERSION)
                .settings(new java.util.ArrayList<>(List.of(
                        setting("auth.registration.enabled", "auth", ValueType.BOOLEAN, "true"),
                        setting("ai.default-system-message", "ai", ValueType.STRING, "hello"),
                        setting("geocoding.googlemaps.api-key", "geocoding", ValueType.ENCRYPTED, "plain-google-key"),
                        setting("weather.enabled", "weather", ValueType.BOOLEAN, "true"))))
                .oidcProviders(new java.util.ArrayList<>(List.of(AdminSettingsBackupDto.OidcProviderBackupDto.builder()
                        .name("keycloak")
                        .displayName("Keycloak")
                        .enabled(true)
                        .clientId("client-id")
                        .clientSecret("plain-oidc-secret")
                        .discoveryUrl("https://idp.example/.well-known/openid-configuration")
                        .scopes("openid profile email")
                        .build())))
                .customGeocodingProviders(new java.util.ArrayList<>(List.of(AdminSettingsBackupDto.CustomGeocodingProviderBackupDto.builder()
                        .name("local-photon")
                        .displayName("Local Photon")
                        .type("photon")
                        .url("https://photon.example")
                        .enabled(true)
                        .headers(Map.of("X-Api-Key", "plain-header-secret"))
                        .build())))
                .build();
    }

    private AdminSettingsBackupDto.SettingBackupDto setting(String key, String category, ValueType valueType, String value) {
        return AdminSettingsBackupDto.SettingBackupDto.builder()
                .key(key)
                .category(category)
                .valueType(valueType)
                .value(value)
                .build();
    }

    private Map<String, SettingDefinition> definitions() {
        Map<String, SettingDefinition> definitions = new LinkedHashMap<>();
        definitions.put("auth.registration.enabled",
                new SettingDefinition("geopulse.auth.registration.enabled", "true", ValueType.BOOLEAN, "auth", "Registration"));
        definitions.put("ai.default-system-message",
                new SettingDefinition("geopulse.ai.default-system-message", "", ValueType.STRING, "ai", "AI message"));
        definitions.put("geocoding.googlemaps.api-key",
                new SettingDefinition("geopulse.geocoding.googlemaps.api-key", "", ValueType.ENCRYPTED, "geocoding", "Google key"));
        definitions.put("weather.enabled",
                new SettingDefinition("geopulse.weather.enabled", "true", ValueType.BOOLEAN, "weather", "Weather"));
        definitions.put("auth.oidc.callback-base-url",
                new SettingDefinition("geopulse.oidc.callback-base-url", "", ValueType.STRING, "auth", "OIDC callback URL"));
        definitions.put("import.geonames.cities.url",
                new SettingDefinition("geopulse.geonames.import.url", "https://download.geonames.org/export/dump/cities500.zip", ValueType.STRING, "import", "GeoNames cities URL"));
        definitions.put("import.drop-folder.path",
                new SettingDefinition("geopulse.import.drop-folder.path", "/data/geopulse-import", ValueType.STRING, "import", "Drop folder path"));
        return definitions;
    }
}
