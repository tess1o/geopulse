package org.github.tess1o.geopulse.admin.service;

import org.github.tess1o.geopulse.admin.dto.UpdateSettingRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class GeocodingValidationServiceTest {

    private final GeocodingValidationService service = createService();

    @Test
    void validateGeocodingChanges_shouldAllowEmptyPhotonLanguage() {
        UpdateSettingRequest request = buildRequest("");

        String error = service.validateGeocodingChanges(List.of(request));

        assertThat(error).isNull();
    }

    @Test
    void validateGeocodingChanges_shouldAllowSimplePhotonLanguageCode() {
        UpdateSettingRequest request = buildRequest("en");

        String error = service.validateGeocodingChanges(List.of(request));

        assertThat(error).isNull();
    }

    @Test
    void validateGeocodingChanges_shouldRejectInvalidPhotonLanguageAndSuggestClosest() {
        UpdateSettingRequest request = buildRequest("en-US");

        String error = service.validateGeocodingChanges(List.of(request));

        assertThat(error).isNotNull();
        assertThat(error).contains("Invalid Photon language 'en-US'");
        assertThat(error).contains("de, pl, el, en, es, fa, fr, it, ja, ko");
        assertThat(error).contains("Try 'en'.");
    }

    @Test
    void validateGeocodingChanges_shouldRejectUnsupportedPhotonLanguageCode() {
        UpdateSettingRequest request = buildRequest("uk");

        String error = service.validateGeocodingChanges(List.of(request));

        assertThat(error).isNotNull();
        assertThat(error).contains("Invalid Photon language 'uk'");
        assertThat(error).contains("de, pl, el, en, es, fa, fr, it, ja, ko");
    }

    @Test
    void validateGeocodingChanges_shouldAllowGeoapifyEnabledAndSelectedWithApiKeyInSameBatch() {
        String error = service.validateGeocodingChanges(List.of(
                buildRequest("geocoding.geoapify.api-key", "geo-key"),
                buildRequest("geocoding.geoapify.enabled", "true"),
                buildRequest("geocoding.primary-provider", "geoapify")
        ));

        assertThat(error).isNull();
    }

    @Test
    void validateGeocodingChanges_shouldRejectGeoapifySelectedWithoutApiKey() {
        String error = service.validateGeocodingChanges(List.of(
                buildRequest("geocoding.geoapify.enabled", "true"),
                buildRequest("geocoding.primary-provider", "geoapify")
        ));

        assertThat(error).contains("Cannot set primary provider to 'geoapify'");
    }

    @Test
    void validateGeocodingChanges_shouldAllowChibiGeoEnabledAndSelectedWithApiKeyInSameBatch() {
        String error = service.validateGeocodingChanges(List.of(
                buildRequest("geocoding.chibigeo.api-key", "chibi-key"),
                buildRequest("geocoding.chibigeo.enabled", "true"),
                buildRequest("geocoding.primary-provider", "chibigeo")
        ));

        assertThat(error).isNull();
    }

    @Test
    void validateGeocodingChanges_shouldValidateChibiGeoLanguageAsPhotonCompatible() {
        String error = service.validateGeocodingChanges(List.of(
                buildRequest("geocoding.chibigeo.language", "en-US")
        ));

        assertThat(error).contains("Invalid Photon language 'en-US'");
    }

    private UpdateSettingRequest buildRequest(String value) {
        return buildRequest("geocoding.photon.language", value);
    }

    private UpdateSettingRequest buildRequest(String key, String value) {
        UpdateSettingRequest request = new UpdateSettingRequest();
        request.setKey(key);
        request.setValue(value);
        return request;
    }

    private GeocodingValidationService createService() {
        GeocodingValidationService validationService = new GeocodingValidationService();
        validationService.settingsService = new StubSettingsService();
        return validationService;
    }

    private static final class StubSettingsService extends SystemSettingsService {
        private StubSettingsService() {
            super(null, null, null);
        }

        @Override
        public String getString(String key) {
            return switch (key) {
                case "geocoding.primary-provider" -> "nominatim";
                case "geocoding.nominatim.enabled" -> "true";
                default -> "";
            };
        }

        @Override
        public boolean getBoolean(String key) {
            return Boolean.parseBoolean(getString(key));
        }
    }
}
