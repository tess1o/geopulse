package org.github.tess1o.geopulse.trips.service;

import io.smallrye.mutiny.Uni;
import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.geocoding.model.common.GeocodingSearchResult;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;
import org.github.tess1o.geopulse.geocoding.service.GeocodingProviderFactory;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
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
        when(geocodingProviderFactory.forwardSearch(eq("berlin"), any(), eq(6)))
                .thenReturn(Uni.createFrom().item(List.of(external)));

        List<PlanSearchResultDto> results = service.search(userId, "berlin", 52.52, 13.40, 3);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getSourceType()).isEqualTo("favorite-point");
        assertThat(results.get(1).getSourceType()).isEqualTo("geocoding");
        assertThat(results.get(2).getSourceType()).isEqualTo("external-search");
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
        when(geocodingProviderFactory.forwardSearch(eq("cafe"), any(), eq(10)))
                .thenReturn(Uni.createFrom().item(List.of(duplicateExternal)));

        List<PlanSearchResultDto> results = service.search(userId, "cafe", null, null, 5);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getSourceType()).isEqualTo("favorite-point");
        assertThat(results.getFirst().getTitle()).isEqualTo("Cafe Blue");
    }

    @Test
    void search_shouldReturnLocalResultsWhenProviderSearchFails() {
        UUID userId = UUID.randomUUID();

        FavoritesEntity favorite = createFavorite(3L, "Local Park", 48.856613, 2.352222, FavoriteLocationType.POINT);

        when(favoritesRepository.findByUserIdAndNameContaining(eq(userId), eq("park"), eq(8)))
                .thenReturn(List.of(favorite));
        when(geocodingRepository.findByDisplayNameContaining(eq(userId), eq("park"), eq(8)))
                .thenReturn(List.of());
        when(geocodingProviderFactory.forwardSearch(eq("park"), any(), eq(8)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Provider unavailable")));

        List<PlanSearchResultDto> results = service.search(userId, "park", null, null, 4);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getSourceType()).isEqualTo("favorite-point");
        assertThat(results.getFirst().getTitle()).isEqualTo("Local Park");
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
