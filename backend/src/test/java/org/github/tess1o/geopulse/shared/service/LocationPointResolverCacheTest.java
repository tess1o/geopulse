package org.github.tess1o.geopulse.shared.service;

import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.geocoding.dto.ReverseGeocodingDTO;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.service.CacheGeocodingBatchService;
import org.github.tess1o.geopulse.geocoding.service.CacheGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.GeocodingService;
import org.github.tess1o.geopulse.geocoding.service.ReverseGeocodingManagementService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class LocationPointResolverCacheTest {

    @Test
    void resolveLocationWithReferences_usesCachedGeocodingBeforeExternalProvider() {
        UUID userId = UUID.randomUUID();
        Point point = GeoUtils.createPoint(103.804579, 1.327946);
        GeocodingService geocodingService = mock(GeocodingService.class);
        FavoriteLocationService favoriteLocationService = mock(FavoriteLocationService.class);
        CacheGeocodingService cacheGeocodingService = mock(CacheGeocodingService.class);
        ReverseGeocodingManagementService managementService = mock(ReverseGeocodingManagementService.class);

        when(favoriteLocationService.findByPoint(userId, point)).thenReturn(null);
        when(cacheGeocodingService.getCachedGeocodingResult(userId, point)).thenReturn(Optional.of(
                SimpleFormattableResult.builder()
                        .requestCoordinates(point)
                        .resultCoordinates(point)
                        .formattedDisplayName("Bukit Timah Rd, Singapore")
                        .providerName("nominatim")
                        .build()
        ));
        when(cacheGeocodingService.getCachedGeocodingResultId(userId, point)).thenReturn(Optional.of(42L));
        when(managementService.normalizeGeocodingForUser(userId, 42L))
                .thenReturn(ReverseGeocodingDTO.builder().id(42L).build());

        LocationPointResolver resolver = new LocationPointResolver(
                geocodingService,
                favoriteLocationService,
                cacheGeocodingService,
                mock(CacheGeocodingBatchService.class),
                managementService
        );

        LocationResolutionResult result = resolver.resolveLocationWithReferences(userId, point);

        assertThat(result.getLocationName()).isEqualTo("Bukit Timah Rd, Singapore");
        assertThat(result.getGeocodingId()).isEqualTo(42L);
        verify(geocodingService, never()).getLocationName(point);
    }
}
