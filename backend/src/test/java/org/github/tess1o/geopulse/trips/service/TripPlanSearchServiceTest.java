package org.github.tess1o.geopulse.trips.service;

import io.smallrye.mutiny.Uni;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.exception.GeocodingException;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.GeocodingSearchResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.geocoding.service.GeocodingProviderFactory;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.trips.model.dto.PlanSearchExternalStatus;
import org.github.tess1o.geopulse.trips.model.dto.PlanSearchResponseDto;
import org.github.tess1o.geopulse.trips.model.dto.PlanSearchResultDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class TripPlanSearchServiceTest {

    @Mock
    FavoritesRepository favoritesRepository;

    @Mock
    ReverseGeocodingLocationRepository geocodingRepository;

    @Mock
    GeocodingProviderFactory geocodingProviderFactory;

    @InjectMocks
    TripPlanSearchService service;

    @Test
    void search_shouldMergeLocalAndExternalResultsWithLocalFirst() {
        UUID userId = UUID.randomUUID();

        FavoritesEntity favorite = createFavorite(1L, "Favorite Museum", 52.520008, 13.404954, FavoriteLocationType.POINT);
        favorite.setCity("Berlin");
        favorite.setCountry("Germany");

        ReverseGeocodingLocationEntity geocoding = createGeocoding(11L, "Brandenburg Gate", 52.516275, 13.377704, "Photon");
        geocoding.setCity("Berlin");
        geocoding.setCountry("Germany");

        GeocodingSearchResult external = GeocodingSearchResult.builder()
                .title("Berlin Central Station")
                .latitude(52.525083)
                .longitude(13.369402)
                .providerName("Photon")
                .city("Berlin")
                .country("Germany")
                .build();

        when(favoritesRepository.findByUserIdAndNameContaining(eq(userId), eq("berlin"), eq(6)))
                .thenReturn(List.of(favorite));
        when(geocodingRepository.findByDisplayNameContaining(eq(userId), eq("berlin"), eq(6)))
                .thenReturn(List.of(geocoding));
        when(geocodingProviderFactory.getPrimaryProvider()).thenReturn("Photon");
        when(geocodingProviderFactory.forwardSearch(eq("berlin"), any(), eq(6)))
                .thenReturn(Uni.createFrom().item(List.of(external)));

        PlanSearchResponseDto response = service.search(userId, "berlin", 52.52, 13.40, 3);
        List<PlanSearchResultDto> results = response.getResults();

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getSourceType()).isEqualTo("favorite-point");
        assertThat(results.get(1).getSourceType()).isEqualTo("geocoding");
        assertThat(results.get(2).getSourceType()).isEqualTo("external-search");
        assertThat(response.getExternalStatus()).isEqualTo(PlanSearchExternalStatus.OK);
        assertThat(response.getExternalMessage()).isNull();
    }

    @Test
    void search_shouldDeduplicateNearbyResultsWithSameTitle() {
        UUID userId = UUID.randomUUID();

        FavoritesEntity favorite = createFavorite(2L, "Cafe Blue", 40.0000, 20.0000, FavoriteLocationType.POINT);
        GeocodingSearchResult duplicateExternal = GeocodingSearchResult.builder()
                .title("cafe blue")
                .latitude(40.0002)
                .longitude(20.0002)
                .providerName("Photon")
                .build();

        when(favoritesRepository.findByUserIdAndNameContaining(eq(userId), eq("cafe"), eq(10)))
                .thenReturn(List.of(favorite));
        when(geocodingRepository.findByDisplayNameContaining(eq(userId), eq("cafe"), eq(10)))
                .thenReturn(List.of());
        when(geocodingProviderFactory.getPrimaryProvider()).thenReturn("Photon");
        when(geocodingProviderFactory.forwardSearch(eq("cafe"), any(), eq(10)))
                .thenReturn(Uni.createFrom().item(List.of(duplicateExternal)));

        PlanSearchResponseDto response = service.search(userId, "cafe", null, null, 5);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().getFirst().getSourceType()).isEqualTo("favorite-point");
        assertThat(response.getResults().getFirst().getTitle()).isEqualTo("Cafe Blue");
    }

    @Test
    void search_shouldReturnLocalResultsAndReportFailureWhenProviderErrors() {
        UUID userId = UUID.randomUUID();

        FavoritesEntity favorite = createFavorite(3L, "Local Park", 48.856613, 2.352222, FavoriteLocationType.POINT);

        when(favoritesRepository.findByUserIdAndNameContaining(eq(userId), eq("park"), eq(8)))
                .thenReturn(List.of(favorite));
        when(geocodingRepository.findByDisplayNameContaining(eq(userId), eq("park"), eq(8)))
                .thenReturn(List.of());
        when(geocodingProviderFactory.getPrimaryProvider()).thenReturn("Photon");
        when(geocodingProviderFactory.forwardSearch(eq("park"), any(), eq(8)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Provider unavailable")));

        PlanSearchResponseDto response = service.search(userId, "park", null, null, 4);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().getFirst().getSourceType()).isEqualTo("favorite-point");
        assertThat(response.getExternalStatus()).isEqualTo(PlanSearchExternalStatus.FAILED);
        assertThat(response.getExternalMessage()).isNotBlank();
    }

    // --- the distinction that was previously impossible to observe -----------------

    @Test
    void search_shouldReportOkWithEmptyResultsWhenProviderFindsNothing() {
        UUID userId = UUID.randomUUID();

        when(favoritesRepository.findByUserIdAndNameContaining(eq(userId), eq("nowhere"), eq(4)))
                .thenReturn(List.of());
        when(geocodingRepository.findByDisplayNameContaining(eq(userId), eq("nowhere"), eq(4)))
                .thenReturn(List.of());
        when(geocodingProviderFactory.getPrimaryProvider()).thenReturn("Photon");
        when(geocodingProviderFactory.forwardSearch(eq("nowhere"), any(), eq(4)))
                .thenReturn(Uni.createFrom().item(List.of()));

        PlanSearchResponseDto response = service.search(userId, "nowhere", null, null, 2);

        assertThat(response.getResults()).isEmpty();
        // A provider answered and simply had no match. NOT a failure.
        assertThat(response.getExternalStatus()).isEqualTo(PlanSearchExternalStatus.OK);
        assertThat(response.getExternalMessage()).isNull();
    }

    @Test
    void search_shouldReportDisabledWhenNoProviderIsConfigured() {
        UUID userId = UUID.randomUUID();

        when(favoritesRepository.findByUserIdAndNameContaining(any(), eq("paris"), anyInt()))
                .thenReturn(List.of());
        when(geocodingRepository.findByDisplayNameContaining(any(), eq("paris"), anyInt()))
                .thenReturn(List.of());
        when(geocodingProviderFactory.getPrimaryProvider()).thenReturn("nominatim");
        when(geocodingProviderFactory.forwardSearch(eq("paris"), any(), anyInt()))
                .thenReturn(Uni.createFrom().failure(
                        new GeocodingException("Photon provider is disabled or not configured")));

        PlanSearchResponseDto response = service.search(userId, "paris", null, null, 2);

        assertThat(response.getExternalStatus()).isEqualTo(PlanSearchExternalStatus.DISABLED);
        assertThat(response.getExternalMessage()).contains("Admin");
    }

    @Test
    void search_shouldExplainPublicNominatimHostRestriction() {
        UUID userId = UUID.randomUUID();

        when(favoritesRepository.findByUserIdAndNameContaining(any(), eq("paris"), anyInt()))
                .thenReturn(List.of());
        when(geocodingRepository.findByDisplayNameContaining(any(), eq("paris"), anyInt()))
                .thenReturn(List.of());
        when(geocodingProviderFactory.getPrimaryProvider()).thenReturn("nominatim");
        when(geocodingProviderFactory.forwardSearch(eq("paris"), any(), anyInt()))
                .thenReturn(Uni.createFrom().failure(new GeocodingException(
                        "Nominatim forward search is disabled for public host by configuration")));

        PlanSearchResponseDto response = service.search(userId, "paris", null, null, 2);

        assertThat(response.getExternalStatus()).isEqualTo(PlanSearchExternalStatus.DISABLED);
        assertThat(response.getExternalMessage())
                .contains("public Nominatim host")
                .contains("public-host-forward-search-enabled");
    }

    @Test
    void search_shouldReportDisabledWhenFallbackWrapsAnUnknownProvider() {
        UUID userId = UUID.randomUUID();

        when(favoritesRepository.findByUserIdAndNameContaining(any(), eq("paris"), anyInt()))
                .thenReturn(List.of());
        when(geocodingRepository.findByDisplayNameContaining(any(), eq("paris"), anyInt()))
                .thenReturn(List.of());
        when(geocodingProviderFactory.getPrimaryProvider()).thenReturn("nominatim");
        // Mirror the real wrapper: factory raises GeocodingException around the cause.
        when(geocodingProviderFactory.forwardSearch(eq("paris"), any(), anyInt()))
                .thenReturn(Uni.createFrom().failure(
                        new GeocodingException("Nominatim forward search failed",
                                new GeocodingException("Unknown provider: photon"))));

        PlanSearchResponseDto response = service.search(userId, "paris", null, null, 2);

        assertThat(response.getExternalStatus()).isEqualTo(PlanSearchExternalStatus.DISABLED);
    }

    private FavoritesEntity createFavorite(Long id, String name, double latitude, double longitude, FavoriteLocationType type) {
        FavoritesEntity favorite = new FavoritesEntity();
        favorite.setId(id);
        favorite.setName(name);
        favorite.setType(type);
        favorite.setGeometry(GeoUtils.createPoint(longitude, latitude));
        return favorite;
    }

    private ReverseGeocodingLocationEntity createGeocoding(Long id, String name, double latitude, double longitude, String providerName) {
        ReverseGeocodingLocationEntity entity = new ReverseGeocodingLocationEntity();
        entity.setId(id);
        entity.setDisplayName(name);
        entity.setProviderName(providerName);
        entity.setRequestCoordinates(GeoUtils.createPoint(longitude, latitude));
        entity.setResultCoordinates(GeoUtils.createPoint(longitude, latitude));
        return entity;
    }
}
