package org.github.tess1o.geopulse.trips.service;

import org.github.tess1o.geopulse.mapmatching.service.MapMatchingConfiguration;
import org.github.tess1o.geopulse.trips.model.dto.TripPlanRouteDto;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemEntity;
import org.github.tess1o.geopulse.trips.model.entity.TripPlanItemTravelMode;
import org.github.tess1o.geopulse.trips.repository.TripPlanItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TripPlanRouteServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long TRIP_ID = 42L;

    @Mock
    TripAccessService tripAccessService;
    @Mock
    TripPlanItemRepository tripPlanItemRepository;
    @Mock
    MapMatchingConfiguration mapMatchingConfiguration;
    @Mock
    TripPlanLegRouter legRouter;

    private TripPlanRouteService service;

    @BeforeEach
    void setUp() {
        service = new TripPlanRouteService(tripAccessService, tripPlanItemRepository, mapMatchingConfiguration, legRouter);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void resolvesAutomaticModeFromDistanceAndHonoursAnExplicitChoice() {
        assertThat(TripPlanRouteService.resolveMode(null, 800)).isEqualTo(TripPlanItemTravelMode.WALK);
        assertThat(TripPlanRouteService.resolveMode(null, 50_000)).isEqualTo(TripPlanItemTravelMode.DRIVE);
        assertThat(TripPlanRouteService.resolveMode(null, 3_000_000)).isEqualTo(TripPlanItemTravelMode.STRAIGHT);
        // An explicit choice wins even where the distance would suggest otherwise.
        assertThat(TripPlanRouteService.resolveMode(TripPlanItemTravelMode.BICYCLE, 800))
                .isEqualTo(TripPlanItemTravelMode.BICYCLE);
        assertThat(TripPlanRouteService.resolveMode(TripPlanItemTravelMode.STRAIGHT, 800))
                .isEqualTo(TripPlanItemTravelMode.STRAIGHT);

        assertThat(TripPlanRouteService.costingFor(TripPlanItemTravelMode.WALK)).isEqualTo("pedestrian");
        assertThat(TripPlanRouteService.costingFor(TripPlanItemTravelMode.BICYCLE)).isEqualTo("bicycle");
        assertThat(TripPlanRouteService.costingFor(TripPlanItemTravelMode.DRIVE)).isEqualTo("auto");
        assertThat(TripPlanRouteService.costingFor(TripPlanItemTravelMode.STRAIGHT)).isNull();
    }

    @Test
    void drawsEveryLegStraightWhenRoutingIsUnavailable() {
        when(mapMatchingConfiguration.isAvailable()).thenReturn(false);
        when(tripPlanItemRepository.findByTripId(TRIP_ID)).thenReturn(List.of(
                item(1L, 50.4501, 30.5234, null),
                item(2L, null, null, null),          // no location: skipped, not a gap in the line
                item(3L, 50.4547, 30.5238, null)));

        TripPlanRouteDto route = service.getPlanRoute(USER_ID, TRIP_ID);

        verify(tripAccessService).requireReadAccess(USER_ID, TRIP_ID);
        verify(legRouter, never()).route(any());
        assertThat(route.isRoutingAvailable()).isFalse();
        assertThat(route.getLegs()).hasSize(1);
        TripPlanRouteDto.Leg leg = route.getLegs().getFirst();
        assertThat(leg.getFromItemId()).isEqualTo(1L);
        assertThat(leg.getToItemId()).isEqualTo(3L);
        assertThat(leg.isRouted()).isFalse();
        assertThat(leg.getCoordinates()).hasSize(2);
        assertThat(leg.getDistanceMeters()).isBetween(400.0, 600.0);
    }

    @Test
    void routesLegsWithTheResolvedCostingAndFallsBackToStraightPerLeg() {
        when(mapMatchingConfiguration.isAvailable()).thenReturn(true);
        when(mapMatchingConfiguration.valhallaBaseUrl()).thenReturn("http://valhalla:8002");
        when(tripPlanItemRepository.findByTripId(TRIP_ID)).thenReturn(List.of(
                item(1L, 50.4501, 30.5234, null),
                item(2L, 50.4547, 30.5238, null),                            // ~0.5 km: walk
                item(3L, 49.8397, 24.0297, null),                            // ~470 km: drive, no route
                item(4L, 49.8400, 24.0300, TripPlanItemTravelMode.STRAIGHT))); // explicit straight

        when(legRouter.route(any())).thenAnswer(invocation -> {
            TripPlanLegRouter.LegKey key = invocation.getArgument(0);
            if ("pedestrian".equals(key.costing())) {
                return new TripPlanLegRouter.RoutedLeg(true,
                        List.of(new double[]{50.4501, 30.5234}, new double[]{50.4520, 30.5240}, new double[]{50.4547, 30.5238}),
                        620.0, 480.0);
            }
            return TripPlanLegRouter.RoutedLeg.UNROUTABLE;
        });

        TripPlanRouteDto route = service.getPlanRoute(USER_ID, TRIP_ID);

        ArgumentCaptor<TripPlanLegRouter.LegKey> keys = ArgumentCaptor.forClass(TripPlanLegRouter.LegKey.class);
        // The explicitly straight leg never reaches Valhalla.
        verify(legRouter, times(2)).route(keys.capture());
        assertThat(keys.getAllValues()).extracting(TripPlanLegRouter.LegKey::costing)
                .containsExactlyInAnyOrder("pedestrian", "auto");

        assertThat(route.isRoutingAvailable()).isTrue();
        assertThat(route.getLegs()).extracting(TripPlanRouteDto.Leg::getToItemId).containsExactly(2L, 3L, 4L);

        TripPlanRouteDto.Leg walk = route.getLegs().get(0);
        assertThat(walk.isRouted()).isTrue();
        assertThat(walk.getResolvedMode()).isEqualTo(TripPlanItemTravelMode.WALK);
        assertThat(walk.getCoordinates()).hasSize(3);
        assertThat(walk.getDistanceMeters()).isEqualTo(620.0);
        assertThat(walk.getDurationSeconds()).isEqualTo(480.0);

        TripPlanRouteDto.Leg noRoute = route.getLegs().get(1);
        assertThat(noRoute.isRouted()).isFalse();
        assertThat(noRoute.getRequestedMode()).isNull();
        assertThat(noRoute.getResolvedMode()).isEqualTo(TripPlanItemTravelMode.STRAIGHT);

        TripPlanRouteDto.Leg straight = route.getLegs().get(2);
        assertThat(straight.isRouted()).isFalse();
        assertThat(straight.getRequestedMode()).isEqualTo(TripPlanItemTravelMode.STRAIGHT);
    }

    @Test
    void drawsALegStraightWhenValhallaFails() {
        when(mapMatchingConfiguration.isAvailable()).thenReturn(true);
        when(mapMatchingConfiguration.valhallaBaseUrl()).thenReturn("http://valhalla:8002");
        when(tripPlanItemRepository.findByTripId(TRIP_ID)).thenReturn(List.of(
                item(1L, 50.4501, 30.5234, null),
                item(2L, 50.5501, 30.6234, null)));
        when(legRouter.route(any())).thenThrow(new RuntimeException("connection refused"));

        TripPlanRouteDto route = service.getPlanRoute(USER_ID, TRIP_ID);

        assertThat(route.getLegs()).hasSize(1);
        assertThat(route.getLegs().getFirst().isRouted()).isFalse();
        assertThat(route.getLegs().getFirst().getCoordinates()).hasSize(2);
    }

    @Test
    void legKeyRoundsCoordinatesSoNearIdenticalStopsShareACacheEntry() {
        TripPlanLegRouter.LegKey a = TripPlanLegRouter.LegKey.of("u", "auto", 50.123456789, 30.1, 50.2, 30.2);
        TripPlanLegRouter.LegKey b = TripPlanLegRouter.LegKey.of("u", "auto", 50.123459999, 30.1, 50.2, 30.2);
        assertThat(a).isEqualTo(b);
    }

    private static TripPlanItemEntity item(Long id, Double latitude, Double longitude, TripPlanItemTravelMode mode) {
        return TripPlanItemEntity.builder()
                .id(id)
                .title("Stop " + id)
                .latitude(latitude)
                .longitude(longitude)
                .travelMode(mode)
                .orderIndex(id.intValue())
                .build();
    }
}
