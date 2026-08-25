package org.github.tess1o.geopulse.geocoding.config;

import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.geocoding.model.CustomGeocodingProviderEntity;
import org.github.tess1o.geopulse.geocoding.service.CustomGeocodingProviderService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class GeocodingConfigurationServiceTest {

    @Test
    void getDelayMsForProvider_shouldUseCustomProviderDelayWhenConfigured() {
        GeocodingConfigurationService service = new GeocodingConfigurationService(
                new StubSettingsService(),
                new StubCustomGeocodingProviderService(customProvider("local-photon", 125))
        );

        assertThat(service.getDelayMsForProvider("local-photon")).isEqualTo(125);
    }

    @Test
    void getDelayMsForProvider_shouldFallBackToGlobalDelayWhenCustomProviderDelayIsEmpty() {
        GeocodingConfigurationService service = new GeocodingConfigurationService(
                new StubSettingsService(),
                new StubCustomGeocodingProviderService(customProvider("local-photon", null))
        );

        assertThat(service.getDelayMsForProvider("local-photon")).isEqualTo(1000);
    }

    @Test
    void getDelayMsForProvider_shouldFallBackToGlobalDelayForUnknownProvider() {
        GeocodingConfigurationService service = new GeocodingConfigurationService(
                new StubSettingsService(),
                new StubCustomGeocodingProviderService(customProvider("local-photon", 125))
        );

        assertThat(service.getDelayMsForProvider("missing-photon")).isEqualTo(1000);
    }

    private static CustomGeocodingProviderEntity customProvider(String name, Integer delayMs) {
        CustomGeocodingProviderEntity provider = new CustomGeocodingProviderEntity();
        provider.setName(name);
        provider.setDelayMs(delayMs);
        return provider;
    }

    private static final class StubSettingsService extends SystemSettingsService {
        private StubSettingsService() {
            super(null, null, null);
        }

        @Override
        public int getInteger(String key) {
            return switch (key) {
                case "geocoding.delay-ms" -> 1000;
                case "geocoding.geoapify.delay-ms" -> 0;
                case "geocoding.chibigeo.delay-ms" -> 0;
                default -> 0;
            };
        }
    }

    private static final class StubCustomGeocodingProviderService extends CustomGeocodingProviderService {
        private final CustomGeocodingProviderEntity provider;

        private StubCustomGeocodingProviderService(CustomGeocodingProviderEntity provider) {
            super(null, null, null, null);
            this.provider = provider;
        }

        @Override
        public Optional<CustomGeocodingProviderEntity> findByName(String name) {
            return provider.getName().equalsIgnoreCase(name) ? Optional.of(provider) : Optional.empty();
        }
    }
}
