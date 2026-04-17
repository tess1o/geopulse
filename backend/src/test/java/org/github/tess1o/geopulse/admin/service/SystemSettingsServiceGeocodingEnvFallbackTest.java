package org.github.tess1o.geopulse.admin.service;

import org.github.tess1o.geopulse.admin.repository.SystemSettingsRepository;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Tag("unit")
class SystemSettingsServiceGeocodingEnvFallbackTest {

    private static final String GEOPULSE_GOOGLE_MAPS_ENABLED = "GEOPULSE_GEOCODING_GOOGLE_MAPS_ENABLED";
    private static final String GEOPULSE_GOOGLE_MAPS_API_KEY = "GEOPULSE_GEOCODING_GOOGLE_MAPS_API_KEY";
    private static final String GEOPULSE_GOOGLE_MAPS_LANGUAGE = "GEOPULSE_GEOCODING_GOOGLE_MAPS_LANGUAGE";
    private static final String PROVIDER_GOOGLE_MAPS_ENABLED = "geocoding.provider.googlemaps.enabled";
    private static final String PROVIDER_GOOGLE_MAPS_API_KEY = "geocoding.provider.googlemaps.api-key";
    private static final String PROVIDER_GOOGLE_MAPS_LANGUAGE = "geocoding.provider.googlemaps.language";

    @AfterEach
    void cleanUpSystemProperties() {
        System.clearProperty(GEOPULSE_GOOGLE_MAPS_ENABLED);
        System.clearProperty(GEOPULSE_GOOGLE_MAPS_API_KEY);
        System.clearProperty(GEOPULSE_GOOGLE_MAPS_LANGUAGE);
        System.clearProperty(PROVIDER_GOOGLE_MAPS_ENABLED);
        System.clearProperty(PROVIDER_GOOGLE_MAPS_API_KEY);
        System.clearProperty(PROVIDER_GOOGLE_MAPS_LANGUAGE);
    }

    @Test
    void shouldResolveGoogleMapsEnabledFromGeopulseEnvVariableStyle() {
        System.setProperty(GEOPULSE_GOOGLE_MAPS_ENABLED, "true");

        SystemSettingsService service = createService();

        assertTrue(service.getBoolean("geocoding.googlemaps.enabled"));
    }

    @Test
    void shouldResolveGoogleMapsEnabledFromProviderPropertyStyle() {
        System.setProperty(PROVIDER_GOOGLE_MAPS_ENABLED, "true");

        SystemSettingsService service = createService();

        assertTrue(service.getBoolean("geocoding.googlemaps.enabled"));
    }

    @Test
    void shouldResolveGoogleMapsApiKeyFromGeopulseEnvVariableStyle() {
        System.setProperty(GEOPULSE_GOOGLE_MAPS_API_KEY, "from-geopulse");

        SystemSettingsService service = createService();

        assertEquals("from-geopulse", service.getString("geocoding.googlemaps.api-key"));
    }

    @Test
    void shouldResolveGoogleMapsApiKeyFromProviderPropertyStyle() {
        System.setProperty(PROVIDER_GOOGLE_MAPS_API_KEY, "from-provider");

        SystemSettingsService service = createService();

        assertEquals("from-provider", service.getString("geocoding.googlemaps.api-key"));
    }

    @Test
    void shouldResolveGoogleMapsLanguageFromGeopulseEnvVariableStyle() {
        System.setProperty(GEOPULSE_GOOGLE_MAPS_LANGUAGE, "uk");

        SystemSettingsService service = createService();

        assertEquals("uk", service.getString("geocoding.googlemaps.language"));
    }

    @Test
    void shouldResolveGoogleMapsLanguageFromProviderPropertyStyle() {
        System.setProperty(PROVIDER_GOOGLE_MAPS_LANGUAGE, "pt-BR");

        SystemSettingsService service = createService();

        assertEquals("pt-BR", service.getString("geocoding.googlemaps.language"));
    }

    private SystemSettingsService createService() {
        SystemSettingsRepository repository = Mockito.mock(SystemSettingsRepository.class);
        AIEncryptionService encryptionService = Mockito.mock(AIEncryptionService.class);
        when(repository.findByKey(anyString())).thenReturn(Optional.empty());
        return new SystemSettingsService(repository, encryptionService);
    }
}
