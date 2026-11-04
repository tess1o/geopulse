package org.github.tess1o.geopulse.geocoding.service.external;

import io.smallrye.mutiny.Uni;
import org.github.tess1o.geopulse.geocoding.model.geoapify.GeoapifyResponse;
import org.github.tess1o.geopulse.geocoding.model.photon.PhotonResponse;
import org.github.tess1o.geopulse.geocoding.adapter.GeoapifyResponseAdapter;
import org.github.tess1o.geopulse.geocoding.adapter.PhotonResponseAdapter;
import org.github.tess1o.geopulse.geocoding.client.GeoapifyRestClient;
import org.github.tess1o.geopulse.geocoding.client.PhotonRestClient;
import org.github.tess1o.geopulse.geocoding.adapter.GoogleMapsResponseAdapter;
import org.github.tess1o.geopulse.geocoding.adapter.MapboxResponseAdapter;
import org.github.tess1o.geopulse.geocoding.client.GoogleMapsRestClient;
import org.github.tess1o.geopulse.geocoding.client.MapboxRestClient;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfigurationService;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingException;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.model.common.GeocodingCandidate;
import org.github.tess1o.geopulse.geocoding.model.googlemaps.GoogleMapsResponse;
import org.github.tess1o.geopulse.geocoding.model.mapbox.MapboxResponse;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ReverseGeocodingRequestsTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void photonKeepsSeparateSingleAndCandidateRequests(boolean candidates) {
        var config = mock(GeocodingConfigurationService.class);
        when(config.isPhotonEnabled()).thenReturn(true);
        when(config.getPhotonLanguage()).thenReturn(Optional.of("en"));
        var client = mock(PhotonRestClient.class);
        var adapter = mock(PhotonResponseAdapter.class);
        var service = new PhotonGeocodingService(adapter, config, "http://unused") {
            @Override PhotonRestClient getClient() { return client; }
        };
        var point = GeoUtils.createPoint(24.0, 49.0);
        var response = new PhotonResponse();
        var result = SimpleFormattableResult.builder().formattedDisplayName("Place").build();
        when(adapter.adapt(response, point, "Photon")).thenReturn(result);
        when(adapter.adaptCandidates(response, point, "Photon")).thenReturn(List.of());
        if (candidates) when(client.getAddressCandidates(24, 49, "en", null, 10)).thenReturn(Uni.createFrom().item(response));
        else when(client.getAddress(24, 49, "en", null)).thenReturn(Uni.createFrom().item(response));
        assertThat((candidates ? service.reverseCandidates(point) : service.reverseGeocode(point)).await().indefinitely())
                .isEqualTo(candidates ? List.of() : result);
        if (candidates) verify(client).getAddressCandidates(24, 49, "en", null, 10);
        else verify(client).getAddress(24, 49, "en", null);
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void geoapifyKeepsSeparateSingleAndCandidateRequests(boolean candidates) {
        var config = mock(GeocodingConfigurationService.class);
        when(config.isGeoapifyEnabled()).thenReturn(true);
        when(config.getGeoapifyApiKey()).thenReturn("key");
        when(config.getGeoapifyLanguage()).thenReturn(Optional.of("uk"));
        var client = mock(GeoapifyRestClient.class);
        var adapter = mock(GeoapifyResponseAdapter.class);
        var service = new GeoapifyGeocodingService(client, adapter, config);
        var point = GeoUtils.createPoint(24.0, 49.0);
        var response = new GeoapifyResponse();
        var result = SimpleFormattableResult.builder().formattedDisplayName("Place").build();
        when(adapter.adapt(response, point, "Geoapify")).thenReturn(result);
        when(adapter.adaptCandidates(response, point, "Geoapify")).thenReturn(List.of());
        if (candidates) when(client.reverseGeocodeCandidates(49, 24, "key", "uk", 10)).thenReturn(Uni.createFrom().item(response));
        else when(client.reverseGeocode(49, 24, "key", "uk")).thenReturn(Uni.createFrom().item(response));
        assertThat((candidates ? service.reverseCandidates(point) : service.reverseGeocode(point)).await().indefinitely())
                .isEqualTo(candidates ? List.of() : result);
        if (candidates) verify(client).reverseGeocodeCandidates(49, 24, "key", "uk", 10);
        else verify(client).reverseGeocode(49, 24, "key", "uk");
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void googleLookupPathsShareRequestOptionsAndFailureHandling(boolean candidates) {
        var config = mock(GeocodingConfigurationService.class);
        when(config.isGoogleMapsEnabled()).thenReturn(true);
        when(config.getGoogleMapsApiKey()).thenReturn("key");
        when(config.getGoogleMapsLanguage()).thenReturn(Optional.of("uk"));
        var client = mock(GoogleMapsRestClient.class);
        var adapter = mock(GoogleMapsResponseAdapter.class);
        var service = new GoogleMapsGeocodingService(client, adapter, config);
        var point = GeoUtils.createPoint(24.0, 49.0);
        var response = new GoogleMapsResponse(); response.setStatus("OK");
        var result = SimpleFormattableResult.builder().formattedDisplayName("Place").build();
        var rows = List.of(new GeocodingCandidate(result, null, null));
        when(adapter.adapt(response, point, "GoogleMaps")).thenReturn(result);
        when(adapter.adaptCandidates(response, point, "GoogleMaps")).thenReturn(rows);
        when(client.reverseGeocode("49.000000,24.000000", "key", "street_address|establishment", "uk"))
                .thenReturn(Uni.createFrom().item(response));
        assertThat((candidates ? service.reverseCandidates(point) : service.reverseGeocode(point)).await().indefinitely())
                .isEqualTo(candidates ? rows : result);
        var failure = new IllegalStateException("Provider unavailable");
        when(client.reverseGeocode("49.000000,24.000000", "key", "street_address|establishment", "uk"))
                .thenReturn(Uni.createFrom().failure(failure));
        assertThatThrownBy(() -> (candidates ? service.reverseCandidates(point) : service.reverseGeocode(point)).await().indefinitely())
                .isInstanceOf(GeocodingException.class).hasCause(failure);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void mapboxLookupPathsShareRequestOptionsAndFailureHandling(boolean candidates) {
        var config = mock(GeocodingConfigurationService.class);
        when(config.isMapboxEnabled()).thenReturn(true);
        when(config.getMapboxAccessToken()).thenReturn("token");
        var client = mock(MapboxRestClient.class);
        var adapter = mock(MapboxResponseAdapter.class);
        var service = new MapboxGeocodingService(client, adapter, config);
        var point = GeoUtils.createPoint(24.0, 49.0);
        var response = new MapboxResponse();
        var result = SimpleFormattableResult.builder().formattedDisplayName("Place").build();
        var rows = List.of(new GeocodingCandidate(result, null, null));
        when(adapter.adapt(response, point, "Mapbox")).thenReturn(result);
        when(adapter.adaptCandidates(response, point, "Mapbox")).thenReturn(rows);
        when(client.reverseGeocode(24, 49, "token", "poi,address")).thenReturn(Uni.createFrom().item(response));
        assertThat((candidates ? service.reverseCandidates(point) : service.reverseGeocode(point)).await().indefinitely())
                .isEqualTo(candidates ? rows : result);
        var failure = new IllegalStateException("Provider unavailable");
        when(client.reverseGeocode(24, 49, "token", "poi,address")).thenReturn(Uni.createFrom().failure(failure));
        assertThatThrownBy(() -> (candidates ? service.reverseCandidates(point) : service.reverseGeocode(point)).await().indefinitely())
                .isInstanceOf(GeocodingException.class).hasCause(failure);
    }
}
