package org.github.tess1o.geopulse.admin.service;

import org.github.tess1o.geopulse.admin.dto.UpdateSettingRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class GeocodingValidationServiceTest {

    private final GeocodingValidationService service = new GeocodingValidationService();

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

    private UpdateSettingRequest buildRequest(String value) {
        UpdateSettingRequest request = new UpdateSettingRequest();
        request.setKey("geocoding.photon.language");
        request.setValue(value);
        return request;
    }
}
