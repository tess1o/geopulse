package org.github.tess1o.geopulse.geocoding.service.external;

import io.smallrye.mutiny.Uni;
import org.github.tess1o.geopulse.geocoding.adapter.PhotonResponseAdapter;
import org.github.tess1o.geopulse.geocoding.client.PhotonRestClient;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfigurationService;
import org.github.tess1o.geopulse.geocoding.model.photon.PhotonResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class PhotonGeocodingServiceLanguageGuardTest {

    @Test
    void sanitizePhotonLanguage_shouldReturnNullForInvalidFormat() {
        TestPhotonService service = createService(new CapturingPhotonClient(), null);

        String sanitized = service.sanitizePhotonLanguage("en-US", "forward search");

        assertThat(sanitized).isNull();
    }

    @Test
    void sanitizePhotonLanguage_shouldReturnSimpleCodeWhenValid() {
        TestPhotonService service = createService(new CapturingPhotonClient(), null);

        String sanitized = service.sanitizePhotonLanguage("en", "forward search");

        assertThat(sanitized).isEqualTo("en");
    }

    @Test
    void forwardSearch_shouldOmitLanguageWhenConfiguredValueIsInvalid() {
        CapturingPhotonClient client = new CapturingPhotonClient();
        TestPhotonService service = createService(client, "en-US");

        service.forwardSearch("Hong Kong", null, 10).await().indefinitely();

        assertThat(client.lastQuery).isEqualTo("Hong Kong");
        assertThat(client.lastLimit).isEqualTo(10);
        assertThat(client.lastLang).isNull();
        assertThat(client.lastAcceptLanguage).isNull();
    }

    @Test
    void forwardSearch_shouldPassLanguageWhenConfiguredValueIsValid() {
        CapturingPhotonClient client = new CapturingPhotonClient();
        TestPhotonService service = createService(client, "en");

        service.forwardSearch("Hong Kong", null, 10).await().indefinitely();

        assertThat(client.lastQuery).isEqualTo("Hong Kong");
        assertThat(client.lastLimit).isEqualTo(10);
        assertThat(client.lastLang).isEqualTo("en");
        assertThat(client.lastAcceptLanguage).isEqualTo("en");
    }

    private TestPhotonService createService(CapturingPhotonClient client, String configuredLanguage) {
        PhotonResponseAdapter adapter = new PhotonResponseAdapter();
        StubGeocodingConfig config = new StubGeocodingConfig();
        config.photonLanguage = configuredLanguage;
        return new TestPhotonService(adapter, config, client);
    }

    private static final class StubGeocodingConfig extends GeocodingConfigurationService {
        private String photonLanguage;

        private StubGeocodingConfig() {
            super(null);
        }

        @Override
        public boolean isPhotonEnabled() {
            return true;
        }

        @Override
        public Optional<String> getPhotonLanguage() {
            if (photonLanguage == null || photonLanguage.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(photonLanguage);
        }
    }

    private static final class TestPhotonService extends PhotonGeocodingService {
        private final PhotonRestClient client;

        private TestPhotonService(PhotonResponseAdapter adapter,
                                  GeocodingConfigurationService configService,
                                  PhotonRestClient client) {
            super(adapter, configService, "http://photon.local");
            this.client = client;
        }

        @Override
        PhotonRestClient getClient() {
            return client;
        }
    }

    private static final class CapturingPhotonClient implements PhotonRestClient {
        private String lastQuery;
        private int lastLimit;
        private String lastLang;
        private String lastAcceptLanguage;

        @Override
        public Uni<PhotonResponse> getAddress(double longitude, double latitude, String acceptLanguage) {
            return Uni.createFrom().item(new PhotonResponse());
        }

        @Override
        public Uni<PhotonResponse> search(String query,
                                          int limit,
                                          Double latitude,
                                          Double longitude,
                                          Integer zoom,
                                          String language,
                                          String acceptLanguage) {
            this.lastQuery = query;
            this.lastLimit = limit;
            this.lastLang = language;
            this.lastAcceptLanguage = acceptLanguage;

            PhotonResponse response = new PhotonResponse();
            response.setFeatures(List.of());
            return Uni.createFrom().item(response);
        }
    }
}
