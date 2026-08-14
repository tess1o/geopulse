package org.github.tess1o.geopulse.streaming.service;

import org.github.tess1o.geopulse.favorites.model.FavoriteLocationType;
import org.github.tess1o.geopulse.favorites.model.FavoritesEntity;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.model.dto.LocationLookupResponseDTO;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TimelineLocationLookupServiceTest {
    @Mock
    TimelineStayRepository stayRepository;

    @Mock
    FavoritesRepository favoritesRepository;

    private TimelineLocationLookupService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TimelineLocationLookupService();
        service.stayRepository = stayRepository;
        service.favoritesRepository = favoritesRepository;
    }

    @Test
    void includesStaysInsideTheMatchedFavoriteAreaEvenWhenFartherThanPointRadius() {
        FavoritesEntity campus = FavoritesEntity.builder()
                .id(7L)
                .name("Campus")
                .type(FavoriteLocationType.AREA)
                .geometry(GeoUtils.createRectangleFromLeafletBounds(50.20, 30.20, 50.10, 30.10))
                .build();

        when(favoritesRepository.findAllByPoint(eq(userId), any(), anyInt(), anyInt()))
                .thenReturn(List.of(campus));
        when(stayRepository.findLocationLookupStays(
                eq(userId), any(), eq(100.0), eq(15.0)))
                .thenReturn(List.<Object[]>of(stayRow(
                        101L,
                        "2026-01-01T12:00:00Z",
                        1800L,
                        "Campus building",
                        50.19,
                        30.19,
                        null,
                        null,
                        12000.0,
                        7L
                )));

        LocationLookupResponseDTO result = service.lookup(userId, 50.11, 30.11);

        assertEquals(1, result.getVisitMatches().size());
        assertEquals(7L, result.getVisitMatches().getFirst().getFavoriteId());
        assertEquals("Inside favorite area", result.getVisitMatches().getFirst().getMatchReason());
        assertEquals(1, result.getVisitMatches().getFirst().getVisitCount());
    }

    @Test
    void returnsNearestFallbackStaysInAscendingDistanceOrderWhenThereIsNoMatch() {
        when(favoritesRepository.findAllByPoint(eq(userId), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(stayRepository.findLocationLookupStays(
                eq(userId), any(), eq(100.0), eq(15.0)))
                .thenReturn(List.of());
        when(stayRepository.findNearestLocationLookupStays(eq(userId), any(), eq(5)))
                .thenReturn(List.<Object[]>of(
                        stayRow(201L, "2026-01-02T12:00:00Z", 600L, "Farther", 50.1, 30.1, null, null, 300.0, null),
                        stayRow(202L, "2026-01-03T12:00:00Z", 600L, "Closer", 50.2, 30.2, null, null, 100.0, null)
                ));

        LocationLookupResponseDTO result = service.lookup(userId, 50.0, 30.0);

        assertTrue(result.getVisitMatches().isEmpty());
        assertEquals(List.of(100.0, 300.0), result.getNearestStays().stream()
                .map(visit -> visit.getDistanceMeters())
                .toList());
    }

    private Object[] stayRow(Long id, String timestamp, long duration, String name,
                             double latitude, double longitude, Long favoriteId,
                             Long geocodingId, double distanceMeters, Long areaId) {
        return new Object[]{
                id,
                Timestamp.from(Instant.parse(timestamp)),
                duration,
                name,
                latitude,
                longitude,
                favoriteId,
                geocodingId,
                distanceMeters,
                areaId
        };
    }
}
