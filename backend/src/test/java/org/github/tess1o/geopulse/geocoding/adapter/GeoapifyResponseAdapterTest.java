package org.github.tess1o.geopulse.geocoding.adapter;

import org.github.tess1o.geopulse.geocoding.exception.GeocodingException;
import org.github.tess1o.geopulse.geocoding.mapper.CountryMapper;
import org.github.tess1o.geopulse.geocoding.model.common.FormattableGeocodingResult;
import org.github.tess1o.geopulse.geocoding.model.geoapify.GeoapifyResponse;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class GeoapifyResponseAdapterTest {

    @Test
    void adapt_shouldMapGeoapifyJsonResponse() {
        GeoapifyResponseAdapter adapter = new GeoapifyResponseAdapter();
        adapter.countryMapper = identityCountryMapper();

        GeoapifyResponse.Bbox bbox = new GeoapifyResponse.Bbox();
        bbox.setLon1(13.404);
        bbox.setLat1(52.519);
        bbox.setLon2(13.406);
        bbox.setLat2(52.521);

        GeoapifyResponse.Result item = new GeoapifyResponse.Result();
        item.setFormatted("Brandenburg Gate, Pariser Platz, Berlin, Germany");
        item.setCity("Berlin");
        item.setCountry("Germany");
        item.setLon(13.404954);
        item.setLat(52.520008);
        item.setBbox(bbox);

        GeoapifyResponse response = new GeoapifyResponse();
        response.setResults(List.of(item));

        FormattableGeocodingResult result = adapter.adapt(
                response,
                GeoUtils.createPoint(13.404954, 52.520008),
                "Geoapify");

        assertThat(result.getProviderName()).isEqualTo("Geoapify");
        assertThat(result.getFormattedDisplayName()).isEqualTo("Brandenburg Gate, Pariser Platz, Berlin, Germany");
        assertThat(result.getCity()).isEqualTo("Berlin");
        assertThat(result.getCountry()).isEqualTo("Germany");
        assertThat(result.getResultCoordinates().getX()).isEqualTo(13.404954);
        assertThat(result.getResultCoordinates().getY()).isEqualTo(52.520008);
        assertThat(result.getBoundingBox()).isNotNull();
    }

    @Test
    void adapt_shouldMapGeoapifyDefaultFeatureResponse() {
        GeoapifyResponseAdapter adapter = new GeoapifyResponseAdapter();
        adapter.countryMapper = identityCountryMapper();

        GeoapifyResponse.Result properties = new GeoapifyResponse.Result();
        properties.setFormatted("Ternopil Oblast, Ukraine");
        properties.setCity("Ternopil");
        properties.setCountry("Ukraine");

        GeoapifyResponse.Geometry geometry = new GeoapifyResponse.Geometry();
        geometry.setType("Point");
        geometry.setCoordinates(List.of(25.56069945, 49.54139765));

        GeoapifyResponse.Feature feature = new GeoapifyResponse.Feature();
        feature.setProperties(properties);
        feature.setGeometry(geometry);
        feature.setBbox(List.of(25.55, 49.53, 25.57, 49.55));

        GeoapifyResponse response = new GeoapifyResponse();
        response.setFeatures(List.of(feature));

        FormattableGeocodingResult result = adapter.adapt(
                response,
                GeoUtils.createPoint(25.56069945, 49.54139765),
                "Geoapify");

        assertThat(result.getProviderName()).isEqualTo("Geoapify");
        assertThat(result.getFormattedDisplayName()).isEqualTo("Ternopil Oblast, Ukraine");
        assertThat(result.getCity()).isEqualTo("Ternopil");
        assertThat(result.getCountry()).isEqualTo("Ukraine");
        assertThat(result.getResultCoordinates().getX()).isEqualTo(25.56069945);
        assertThat(result.getResultCoordinates().getY()).isEqualTo(49.54139765);
        assertThat(result.getBoundingBox()).isNotNull();
    }

    @Test
    void adapt_shouldFailForEmptyResponse() {
        GeoapifyResponseAdapter adapter = new GeoapifyResponseAdapter();
        adapter.countryMapper = identityCountryMapper();

        GeoapifyResponse response = new GeoapifyResponse();
        response.setResults(List.of());

        assertThatThrownBy(() -> adapter.adapt(response, GeoUtils.createPoint(13.404954, 52.520008), "Geoapify"))
                .isInstanceOf(GeocodingException.class)
                .hasMessageContaining("Geoapify returned empty");
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
