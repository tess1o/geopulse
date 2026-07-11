package org.github.tess1o.geopulse.geocoding.service.external;

import io.smallrye.mutiny.Uni;
import org.github.tess1o.geopulse.geocoding.adapter.PhotonResponseAdapter;
import org.github.tess1o.geopulse.geocoding.client.PhotonRestClient;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfigurationService;
import org.github.tess1o.geopulse.geocoding.mapper.CountryMapper;
import org.github.tess1o.geopulse.geocoding.model.common.GeocodingSearchResult;
import org.github.tess1o.geopulse.geocoding.model.photon.PhotonResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@Tag("unit")
class ChibiGeoGeocodingServiceTest {

    @Test
    void forwardSearch_shouldSendApiKeyHeaderAndUseChibiGeoProviderName() {
        CapturingPhotonClient client = new CapturingPhotonClient();
        TestChibiGeoService service = createService(client);

        List<GeocodingSearchResult> results = service.forwardSearch("Berlin", null, 5).await().indefinitely();

        assertThat(client.lastApiKey).isEqualTo("chibi-key");
        assertThat(client.lastQuery).isEqualTo("Berlin");
        assertThat(client.lastLang).isEqualTo("en");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getProviderName()).isEqualTo("ChibiGeo");
    }

    private TestChibiGeoService createService(CapturingPhotonClient client) {
        PhotonResponseAdapter adapter = new PhotonResponseAdapter();
        setCountryMapper(adapter, identityCountryMapper());
        return new TestChibiGeoService(adapter, new StubGeocodingConfig(), client.client());
    }

    private void setCountryMapper(PhotonResponseAdapter adapter, CountryMapper countryMapper) {
        try {
            var field = PhotonResponseAdapter.class.getDeclaredField("countryMapper");
            field.setAccessible(true);
            field.set(adapter, countryMapper);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to configure test adapter", e);
        }
    }

    private CountryMapper identityCountryMapper() {
        return new CountryMapper() {
            @Override
            public String normalize(String countryName) {
                return countryName;
            }
        };
    }

    private static final class StubGeocodingConfig extends GeocodingConfigurationService {
        private StubGeocodingConfig() {
            super(null);
        }

        @Override
        public boolean isChibiGeoEnabled() {
            return true;
        }

        @Override
        public String getChibiGeoApiKey() {
            return "chibi-key";
        }

        @Override
        public Optional<String> getChibiGeoUrl() {
            return Optional.of("https://app.chibigeo.com/v1/photon");
        }

        @Override
        public Optional<String> getChibiGeoLanguage() {
            return Optional.of("en");
        }
    }

    private static final class TestChibiGeoService extends ChibiGeoGeocodingService {
        private final PhotonRestClient client;

        private TestChibiGeoService(PhotonResponseAdapter adapter,
                                    GeocodingConfigurationService configService,
                                    PhotonRestClient client) {
            super(adapter, configService, "https://app.chibigeo.com/v1/photon");
            this.client = client;
        }

        @Override
        PhotonRestClient getClient() {
            return client;
        }
    }

    private static final class CapturingPhotonClient {
        private final PhotonRestClient client = mock(PhotonRestClient.class);
        private String lastQuery;
        private String lastLang;
        private String lastApiKey;

        private CapturingPhotonClient() {
            doAnswer(invocation -> {
                lastApiKey = invocation.getArgument(3);
                return Uni.createFrom().item(response());
            }).when(client).getAddress(
                    anyDouble(),
                    anyDouble(),
                    nullable(String.class),
                    nullable(String.class));

            doAnswer(invocation -> {
                lastQuery = invocation.getArgument(0);
                lastLang = invocation.getArgument(5);
                lastApiKey = invocation.getArgument(7);
                return Uni.createFrom().item(response());
            }).when(client).search(
                    anyString(),
                    anyInt(),
                    nullable(Double.class),
                    nullable(Double.class),
                    nullable(Integer.class),
                    nullable(String.class),
                    nullable(String.class),
                    nullable(String.class));
        }

        private PhotonRestClient client() {
            return client;
        }

        private PhotonResponse response() {
            PhotonResponse.Geometry geometry = new PhotonResponse.Geometry();
            geometry.setCoordinates(List.of(13.404954, 52.520008));

            PhotonResponse.Properties properties = new PhotonResponse.Properties();
            properties.setName("Berlin");
            properties.setType("city");
            properties.setCountry("Germany");

            PhotonResponse.Feature feature = new PhotonResponse.Feature();
            feature.setGeometry(geometry);
            feature.setProperties(properties);

            PhotonResponse response = new PhotonResponse();
            response.setFeatures(List.of(feature));
            return response;
        }
    }
}
