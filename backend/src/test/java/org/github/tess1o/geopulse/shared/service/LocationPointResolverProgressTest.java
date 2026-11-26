package org.github.tess1o.geopulse.shared.service;

import org.github.tess1o.geopulse.favorites.service.FavoriteLocationService;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfigurationService;
import org.github.tess1o.geopulse.geocoding.model.common.SimpleFormattableResult;
import org.github.tess1o.geopulse.geocoding.service.CacheGeocodingBatchService;
import org.github.tess1o.geopulse.geocoding.service.CacheGeocodingService;
import org.github.tess1o.geopulse.geocoding.service.GeocodingService;
import org.github.tess1o.geopulse.geocoding.service.ReverseGeocodingManagementService;
import org.github.tess1o.geopulse.shared.api.MessageDescriptor;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class LocationPointResolverProgressTest {

    @Test
    void reverseGeocodingProgressUsesFiftyFiveToSeventyRange() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Point point1 = GeoUtils.createPoint(30.5234, 50.4501);
        Point point2 = GeoUtils.createPoint(30.5235, 50.4502);

        GeocodingService geocodingService = mock(GeocodingService.class);
        FavoriteLocationService favoriteLocationService = mock(FavoriteLocationService.class);
        CacheGeocodingService cacheGeocodingService = mock(CacheGeocodingService.class);
        CacheGeocodingBatchService batchService = mock(CacheGeocodingBatchService.class);
        ReverseGeocodingManagementService managementService = mock(ReverseGeocodingManagementService.class);
        GeocodingConfigurationService configurationService = mock(GeocodingConfigurationService.class);
        TimelineJobProgressService progressService = mock(TimelineJobProgressService.class);

        when(favoriteLocationService.findByPointsBatch(eq(userId), anyList())).thenReturn(Map.of());
        when(batchService.getCachedPlacesBatch(eq(userId), anyList()))
                .thenReturn(new CacheGeocodingBatchService.CachedPlaces(Map.of(), List.of()));
        when(cacheGeocodingService.getCachedGeocodingResultId(eq(userId), any(Point.class)))
                .thenReturn(Optional.empty());
        when(configurationService.getPrimaryProviderDelayMs()).thenReturn(0);
        when(geocodingService.getLocationName(any(Point.class)))
                .thenAnswer(invocation -> {
                    Point point = invocation.getArgument(0);
                    return SimpleFormattableResult.builder()
                            .requestCoordinates(point)
                            .resultCoordinates(point)
                            .formattedDisplayName("Resolved location")
                            .providerName("test")
                            .build();
                });

        LocationPointResolver resolver = new LocationPointResolver(
                geocodingService,
                favoriteLocationService,
                cacheGeocodingService,
                batchService,
                managementService,
                configurationService
        );
        resolver.jobProgressService = progressService;

        resolver.resolveLocationsWithReferencesBatch(userId, List.of(point1, point2), jobId);

        ArgumentCaptor<Integer> percentageCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(progressService, org.mockito.Mockito.atLeastOnce()).updateProgress(
                eq(jobId),
                any(MessageDescriptor.class),
                eq(4),
                percentageCaptor.capture(),
                any()
        );

        assertThat(percentageCaptor.getAllValues()).allSatisfy(percentage ->
                assertThat(percentage).isBetween(55, 70));
        assertThat(percentageCaptor.getAllValues()).first().isEqualTo(55);
        assertThat(percentageCaptor.getAllValues()).contains(70);
    }

    @Test
    void chunksShareOneProgressAndKeepTheProviderDelayBetweenThem() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        GeocodingService geocodingService = mock(GeocodingService.class);
        FavoriteLocationService favoriteLocationService = mock(FavoriteLocationService.class);
        CacheGeocodingService cacheGeocodingService = mock(CacheGeocodingService.class);
        CacheGeocodingBatchService batchService = mock(CacheGeocodingBatchService.class);
        ReverseGeocodingManagementService managementService = mock(ReverseGeocodingManagementService.class);
        GeocodingConfigurationService configurationService = mock(GeocodingConfigurationService.class);
        TimelineJobProgressService progressService = mock(TimelineJobProgressService.class);

        when(favoriteLocationService.findByPointsBatch(eq(userId), anyList())).thenReturn(Map.of());
        when(batchService.getCachedPlacesBatch(eq(userId), anyList()))
                .thenReturn(new CacheGeocodingBatchService.CachedPlaces(Map.of(), List.of()));
        when(cacheGeocodingService.getCachedGeocodingResultId(eq(userId), any(Point.class))).thenReturn(Optional.empty());
        when(configurationService.getPrimaryProviderDelayMs()).thenReturn(300);
        List<Long> requestTimes = new java.util.ArrayList<>();
        when(geocodingService.getLocationName(any(Point.class))).thenAnswer(invocation -> {
            requestTimes.add(System.nanoTime());
            Point point = invocation.getArgument(0);
            return SimpleFormattableResult.builder().requestCoordinates(point).resultCoordinates(point)
                    .formattedDisplayName("Place " + point.getY()).providerName("test").build();
        });

        LocationPointResolver resolver = new LocationPointResolver(geocodingService, favoriteLocationService,
                cacheGeocodingService, batchService, managementService, configurationService);
        resolver.jobProgressService = progressService;

        Point first = GeoUtils.createPoint(30.5234, 50.4501);
        Point second = GeoUtils.createPoint(30.6234, 50.5501);
        LocationPointResolver.LocationBatch batch = resolver.startBatch(userId, jobId, 2);
        Map<String, LocationResolutionResult> firstChunk = resolver.resolveChunk(batch, List.of(first));
        Map<String, LocationResolutionResult> secondChunk = resolver.resolveChunk(batch, List.of(second));
        resolver.finishBatch(batch);

        assertThat(firstChunk.get(LocationPointResolver.coordinateKey(first)).getLocationName()).isEqualTo("Place 50.4501");
        assertThat(secondChunk.get(LocationPointResolver.coordinateKey(second)).getLocationName()).isEqualTo("Place 50.5501");
        // The second chunk's request waits for the provider delay after the first chunk's
        assertThat(requestTimes).hasSize(2);
        assertThat((requestTimes.get(1) - requestTimes.get(0)) / 1_000_000).isGreaterThanOrEqualTo(300);

        ArgumentCaptor<Integer> percentages = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(progressService, org.mockito.Mockito.atLeastOnce()).updateProgress(
                eq(jobId), any(MessageDescriptor.class), eq(4), percentages.capture(), details.capture());
        assertThat(percentages.getAllValues()).first().isEqualTo(55);
        assertThat(percentages.getAllValues()).isSorted().last().isEqualTo(70);
        assertThat(details.getAllValues()).allSatisfy(d -> assertThat(d).containsEntry("totalLocations", 2));
        assertThat(details.getAllValues().getLast()).containsEntry("externalCompleted", 2).containsEntry("totalResolved", 2);
    }
}
