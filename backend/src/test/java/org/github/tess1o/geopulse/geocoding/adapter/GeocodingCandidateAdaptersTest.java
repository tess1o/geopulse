package org.github.tess1o.geopulse.geocoding.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.tess1o.geopulse.geocoding.mapper.CountryMapper;
import org.github.tess1o.geopulse.geocoding.model.photon.PhotonResponse;
import org.github.tess1o.geopulse.geocoding.model.googlemaps.GoogleMapsResponse;
import org.github.tess1o.geopulse.geocoding.model.mapbox.MapboxResponse;
import org.github.tess1o.geopulse.geocoding.model.geoapify.GeoapifyResponse;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class GeocodingCandidateAdaptersTest {
    final ObjectMapper json = new ObjectMapper();
    final org.locationtech.jts.geom.Point request = GeoUtils.createPoint(-1.061154, 54.245793);
    final CountryMapper countries = new CountryMapper() {
        @Override public String normalize(String country) { return country; }
    };

    @Test
    void photonPreservesOrderMetadataRequestPointAndSkipsMalformedFeatures() throws Exception {
        var adapter = new PhotonResponseAdapter();
        adapter.countryMapper = countries;
        var response = json.readValue("""
                {"features":[{},
                  {"properties":{"name":"First","osm_value":"cafe","street":"High Street"},"geometry":{"coordinates":[-1.061,54.246]}},
                  {"properties":{"name":"Second"},"geometry":{"coordinates":[-1.062,54.247]}}]}
                """, PhotonResponse.class);
        var results = adapter.adaptCandidates(response, request, "Custom Photon");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).category()).isEqualTo("cafe");
        assertThat(results.get(0).address()).contains("High Street");
        assertThat(results.get(1).result().getFormattedDisplayName()).isEqualTo("Second");
        assertThat(results.get(1).result().getRequestCoordinates()).isEqualTo(request);
        assertThat(results.get(1).result().getResultCoordinates()).isNotEqualTo(request);
        assertThat(results.get(1).result().getProviderName()).isEqualTo("Custom Photon");
        response.setFeatures(Collections.nCopies(15, response.getFeatures().get(1)));
        assertThat(adapter.adaptCandidates(response, request, "Photon")).hasSize(10);
        assertThat(adapter.adaptCandidates(null, request, "Photon")).isEmpty();
    }

    @Test
    void googlePreservesResultsAndHandlesMissingMetadata() throws Exception {
        var adapter = new GoogleMapsResponseAdapter();
        adapter.countryMapper = countries;
        var response = json.readValue("""
                {"results":[{}, {"formatted_address":"First","types":["street_address"],"geometry":{"location":{"lat":54.246,"lng":-1.061}}},
                {"formatted_address":"Second","geometry":{"location":{"lat":54.247,"lng":-1.062}}}]}
                """, GoogleMapsResponse.class);
        var results = adapter.adaptCandidates(response, request, "GoogleMaps");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).category()).isEqualTo("street_address");
        assertThat(results.get(1).result().getFormattedDisplayName()).isEqualTo("Second");
        assertThat(results.get(1).result().getRequestCoordinates()).isEqualTo(request);
        assertThat(adapter.adaptCandidates(new GoogleMapsResponse(), request, "GoogleMaps")).isEmpty();
    }

    @Test
    void mapboxPreservesFeaturesAndHandlesMissingMetadata() throws Exception {
        var adapter = new MapboxResponseAdapter();
        adapter.countryMapper = countries;
        var response = json.readValue("""
                {"features":[{}, {"place_name":"First","text":"First","properties":{"category":"cafe"},"geometry":{"coordinates":[-1.061,54.246]}},
                {"place_name":"Second","text":"Second","geometry":{"coordinates":[-1.062,54.247]}}]}
                """, MapboxResponse.class);
        var results = adapter.adaptCandidates(response, request, "Mapbox");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).category()).isEqualTo("cafe");
        assertThat(results.get(1).result().getFormattedDisplayName()).contains("Second");
        assertThat(results.get(1).result().getRequestCoordinates()).isEqualTo(request);
        assertThat(adapter.adaptCandidates(null, request, "Mapbox")).isEmpty();
    }

    @Test
    void geoapifyHandlesBothJsonAndGeoJsonShapes() throws Exception {
        var adapter = new GeoapifyResponseAdapter();
        adapter.countryMapper = countries;
        for (var document : List.of("""
                {"results":[{}, {"formatted":"First","lon":-1.061,"lat":54.246},
                {"formatted":"Second","lon":-1.062,"lat":54.247}]}
                """, """
                {"features":[{}, {"properties":{"formatted":"First"},"geometry":{"coordinates":[-1.061,54.246]}},
                {"properties":{"formatted":"Second"},"geometry":{"coordinates":[-1.062,54.247]}}]}
                """)) {
            var results = adapter.adaptCandidates(json.readValue(document, GeoapifyResponse.class), request, "Geoapify");
            assertThat(results).hasSize(2);
            assertThat(results.get(1).result().getFormattedDisplayName()).isEqualTo("Second");
            assertThat(results.get(1).result().getRequestCoordinates()).isEqualTo(request);
        }
        assertThat(adapter.adaptCandidates(new GeoapifyResponse(), request, "Geoapify")).isEmpty();
    }

    @Test
    void nominatimHasOneCandidateWithAvailableType() throws Exception {
        var adapter = new NominatimResponseAdapter(
                new org.github.tess1o.geopulse.geocoding.model.nominatim.NominatimAddressFormatter(), countries);
        var response = json.readValue("""
                {"lat":"54.246","lon":"-1.061","type":"cafe","display_name":"Coffee house",
                 "address":{"city":"Helmsley","country":"United Kingdom"}}
                """, org.github.tess1o.geopulse.geocoding.model.nominatim.NominatimResponse.class);
        var results = adapter.adaptCandidates(response, request, "Custom Nominatim");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().category()).isEqualTo("cafe");
        assertThat(results.getFirst().result().getRequestCoordinates()).isEqualTo(request);
        assertThat(adapter.adaptCandidates(null, request, "Nominatim")).isEmpty();
    }
}
