package org.github.tess1o.geopulse.admin.service;

import jakarta.enterprise.event.Event;
import org.github.tess1o.geopulse.admin.model.SettingInfo;
import org.github.tess1o.geopulse.admin.repository.SystemSettingsRepository;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.user.model.MeasureUnit;
import org.github.tess1o.geopulse.weather.event.WeatherSettingsChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class SystemSettingsServiceGeocodingEnvFallbackTest {

    private static final String GEOPULSE_GOOGLE_MAPS_ENABLED = "GEOPULSE_GEOCODING_GOOGLE_MAPS_ENABLED";
    private static final String GEOPULSE_GOOGLE_MAPS_API_KEY = "GEOPULSE_GEOCODING_GOOGLE_MAPS_API_KEY";
    private static final String GEOPULSE_GOOGLE_MAPS_LANGUAGE = "GEOPULSE_GEOCODING_GOOGLE_MAPS_LANGUAGE";
    private static final String GEOPULSE_GEOAPIFY_API_KEY = "GEOPULSE_GEOCODING_GEOAPIFY_API_KEY";
    private static final String GEOPULSE_CHIBIGEO_API_KEY = "GEOPULSE_GEOCODING_CHIBIGEO_API_KEY";
    private static final String PROVIDER_GOOGLE_MAPS_ENABLED = "geocoding.provider.googlemaps.enabled";
    private static final String PROVIDER_GOOGLE_MAPS_API_KEY = "geocoding.provider.googlemaps.api-key";
    private static final String PROVIDER_GOOGLE_MAPS_LANGUAGE = "geocoding.provider.googlemaps.language";
    private static final String PROVIDER_GEOAPIFY_API_KEY = "geocoding.provider.geoapify.api-key";
    private static final String PROVIDER_CHIBIGEO_API_KEY = "geocoding.provider.chibigeo.api-key";
    private static final String GEOPULSE_USER_DEFAULT_MEASURE_UNIT = "GEOPULSE_USER_DEFAULT_MEASURE_UNIT";
    private static final String USER_DEFAULT_MEASURE_UNIT = "geopulse.user.default-measure-unit";

    @AfterEach
    void cleanUpSystemProperties() {
        System.clearProperty(GEOPULSE_GOOGLE_MAPS_ENABLED);
        System.clearProperty(GEOPULSE_GOOGLE_MAPS_API_KEY);
        System.clearProperty(GEOPULSE_GOOGLE_MAPS_LANGUAGE);
        System.clearProperty(GEOPULSE_GEOAPIFY_API_KEY);
        System.clearProperty(GEOPULSE_CHIBIGEO_API_KEY);
        System.clearProperty(PROVIDER_GOOGLE_MAPS_ENABLED);
        System.clearProperty(PROVIDER_GOOGLE_MAPS_API_KEY);
        System.clearProperty(PROVIDER_GOOGLE_MAPS_LANGUAGE);
        System.clearProperty(PROVIDER_GEOAPIFY_API_KEY);
        System.clearProperty(PROVIDER_CHIBIGEO_API_KEY);
        System.clearProperty(GEOPULSE_USER_DEFAULT_MEASURE_UNIT);
        System.clearProperty(USER_DEFAULT_MEASURE_UNIT);
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

    @Test
    void shouldTreatMissingGeoapifyApiKeyAsEmpty() {
        SystemSettingsService service = createService();

        assertEquals("", service.getString("geocoding.geoapify.api-key"));
    }

    @Test
    void shouldTreatMissingChibiGeoApiKeyAsEmpty() {
        SystemSettingsService service = createService();

        assertEquals("", service.getString("geocoding.chibigeo.api-key"));
    }

    @Test
    void shouldReturnEmptyValuesForMissingEncryptedGeoapifyAndChibiGeoCredentials() {
        SystemSettingsService service = createService();

        List<SettingInfo> geocodingSettings = service.getSettingsByCategory("geocoding");
        SettingInfo geoapifyApiKey = findSetting(geocodingSettings, "geocoding.geoapify.api-key");
        SettingInfo chibiGeoApiKey = findSetting(geocodingSettings, "geocoding.chibigeo.api-key");

        assertEquals("", geoapifyApiKey.value());
        assertEquals("", geoapifyApiKey.defaultValue());
        assertEquals("", chibiGeoApiKey.value());
        assertEquals("", chibiGeoApiKey.defaultValue());
    }

    @Test
    void shouldMaskRealGeoapifyAndChibiGeoEnvCredentials() {
        System.setProperty(GEOPULSE_GEOAPIFY_API_KEY, "geoapify-key");
        System.setProperty(GEOPULSE_CHIBIGEO_API_KEY, "chibigeo-key");

        SystemSettingsService service = createService();

        assertEquals("geoapify-key", service.getString("geocoding.geoapify.api-key"));
        assertEquals("chibigeo-key", service.getString("geocoding.chibigeo.api-key"));

        List<SettingInfo> geocodingSettings = service.getSettingsByCategory("geocoding");
        assertEquals("********", findSetting(geocodingSettings, "geocoding.geoapify.api-key").value());
        assertEquals("********", findSetting(geocodingSettings, "geocoding.chibigeo.api-key").value());
    }

    @Test
    void shouldUseMetricAsDefaultMeasureUnit() {
        SystemSettingsService service = createService();

        assertEquals(MeasureUnit.METRIC, service.getDefaultMeasureUnit());
        assertEquals("METRIC", service.getString("system.user.default-measure-unit"));
    }

    @Test
    void shouldResolveDefaultMeasureUnitFromGeopulseEnvVariableStyle() {
        System.setProperty(GEOPULSE_USER_DEFAULT_MEASURE_UNIT, "IMPERIAL");

        SystemSettingsService service = createService();

        assertEquals(MeasureUnit.IMPERIAL, service.getDefaultMeasureUnit());
    }

    @Test
    void shouldResolveDefaultMeasureUnitFromPropertyStyle() {
        System.setProperty(USER_DEFAULT_MEASURE_UNIT, "IMPERIAL");

        SystemSettingsService service = createService();

        assertEquals(MeasureUnit.IMPERIAL, service.getDefaultMeasureUnit());
    }

    @Test
    void shouldFallbackToMetricWhenDefaultMeasureUnitEnvValueIsInvalid() {
        System.setProperty(GEOPULSE_USER_DEFAULT_MEASURE_UNIT, "yards");

        SystemSettingsService service = createService();

        assertEquals(MeasureUnit.METRIC, service.getDefaultMeasureUnit());
        assertEquals("METRIC", service.getString("system.user.default-measure-unit"));
    }

    @Test
    void shouldRejectInvalidDefaultMeasureUnitAdminValue() {
        SystemSettingsService service = createService();

        assertThrows(IllegalArgumentException.class,
                () -> service.setValue("system.user.default-measure-unit", "yards", null));
    }

    @Test
    void shouldFireWeatherSettingsChangedEventWhenWeatherSettingIsUpdated() {
        SystemSettingsRepository repository = Mockito.mock(SystemSettingsRepository.class);
        AIEncryptionService encryptionService = Mockito.mock(AIEncryptionService.class);
        @SuppressWarnings("unchecked")
        Event<WeatherSettingsChangedEvent> weatherSettingsChangedEvent = Mockito.mock(Event.class);
        when(repository.findByKey(anyString())).thenReturn(Optional.empty());
        SystemSettingsService service = new SystemSettingsService(repository, encryptionService, weatherSettingsChangedEvent);

        service.setValue("weather.enabled", "true", null);

        verify(weatherSettingsChangedEvent).fire(new WeatherSettingsChangedEvent("weather.enabled"));
    }

    @Test
    void shouldNotFireWeatherSettingsChangedEventWhenNonWeatherSettingIsUpdated() {
        SystemSettingsRepository repository = Mockito.mock(SystemSettingsRepository.class);
        AIEncryptionService encryptionService = Mockito.mock(AIEncryptionService.class);
        @SuppressWarnings("unchecked")
        Event<WeatherSettingsChangedEvent> weatherSettingsChangedEvent = Mockito.mock(Event.class);
        when(repository.findByKey(anyString())).thenReturn(Optional.empty());
        SystemSettingsService service = new SystemSettingsService(repository, encryptionService, weatherSettingsChangedEvent);

        service.setValue("auth.registration.enabled", "true", null);

        verify(weatherSettingsChangedEvent, never()).fire(any());
    }

    private SystemSettingsService createService() {
        SystemSettingsRepository repository = Mockito.mock(SystemSettingsRepository.class);
        AIEncryptionService encryptionService = Mockito.mock(AIEncryptionService.class);
        @SuppressWarnings("unchecked")
        Event<WeatherSettingsChangedEvent> weatherSettingsChangedEvent = Mockito.mock(Event.class);
        when(repository.findByKey(anyString())).thenReturn(Optional.empty());
        return new SystemSettingsService(repository, encryptionService, weatherSettingsChangedEvent);
    }

    private SettingInfo findSetting(List<SettingInfo> settings, String key) {
        return settings.stream()
                .filter(setting -> setting.key().equals(key))
                .findFirst()
                .orElseThrow();
    }
}
