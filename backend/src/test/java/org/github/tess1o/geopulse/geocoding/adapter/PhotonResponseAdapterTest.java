package org.github.tess1o.geopulse.geocoding.adapter;

import org.github.tess1o.geopulse.geocoding.mapper.CountryMapper;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.photon.PhotonResponse;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class PhotonResponseAdapterTest {

    @Test
    void adapt_shouldUseProvidedProviderName() {
        PhotonResponseAdapter adapter = new PhotonResponseAdapter();
        adapter.countryMapper = identityCountryMapper();

        PhotonResponse.Geometry geometry = new PhotonResponse.Geometry();
        geometry.setCoordinates(List.of(13.404954, 52.520008));

        PhotonResponse.Properties properties = new PhotonResponse.Properties();
        properties.setName("Brandenburg Gate");
        properties.setStreet("Pariser Platz");
        properties.setCountry("Germany");

        PhotonResponse.Feature feature = new PhotonResponse.Feature();
        feature.setGeometry(geometry);
        feature.setProperties(properties);

        PhotonResponse response = new PhotonResponse();
        response.setFeatures(List.of(feature));

        FormattableGeocodingResult result = adapter.adapt(
                response,
                GeoUtils.createPoint(13.404954, 52.520008),
                "ChibiGeo");

        assertThat(result.getProviderName()).isEqualTo("ChibiGeo");
        assertThat(result.getFormattedDisplayName()).isEqualTo("Brandenburg Gate (Pariser Platz)");
    }

    private CountryMapper identityCountryMapper() {
        return new CountryMapper() {
            @Override
            public String normalize(String countryName) {
                return countryName;
            }
        };
    }
}
