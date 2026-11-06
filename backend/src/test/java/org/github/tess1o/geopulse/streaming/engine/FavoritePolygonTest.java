package org.github.tess1o.geopulse.streaming.engine;

import org.github.tess1o.geopulse.favorites.model.AreaGeometry;
import org.github.tess1o.geopulse.favorites.model.FavoriteAreaDto;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class FavoritePolygonTest {
    private AreaGeometry shape() {
        return new AreaGeometry("Polygon", List.of(List.of(
                List.of(30., 50.), List.of(30.03, 50.), List.of(30.03, 50.01),
                List.of(30.01, 50.01), List.of(30.01, 50.03), List.of(30., 50.03), List.of(30., 50.))));
    }
    private GPSPoint point(double lon, double lat, int minute) {
        return GPSPoint.builder().longitude(lon).latitude(lat).timestamp(Instant.parse("2026-09-03T08:00:00Z").plusSeconds(minute * 60L)).accuracy(5.).build();
    }
    @Test void coversConcavePolygonAndBoundaryButNotItsEnvelope() {
        var area = FavoriteAreaDto.builder().id(1).geometry(shape()).build();
        assertThat(FavoriteAreas.containing(point(30.005, 50.02, 0), List.of(area))).isSameAs(area);
        assertThat(FavoriteAreas.containing(point(30.02, 50.02, 0), List.of(area))).isNull();
        assertThat(FavoriteAreas.containing(point(30.01, 50.02, 0), List.of(area))).isSameAs(area);
        assertThat(FavoriteAreas.containing(point(30, 50, 0), List.of(area))).isSameAs(area);
        assertThat(area.preparedGeometry()).isSameAs(area.preparedGeometry());
    }
    @Test void finalizesInsideFavoriteWhenAverageFallsInMissingCorner() {
        var state = new UserState();
        state.startExplanation(TimelineReason.of(TimelineReasons.FIRST_POINT));
        state.setFavoriteAreas(List.of(FavoriteAreaDto.builder().id(1).geometry(shape()).build()));
        state.addActivePoint(point(30.025, 50.005, 0));
        state.addActivePoint(point(30.005, 50.025, 10));
        var stay = new TimelineEventFinalizationService().finalizeStayWithoutLocation(state,
                TimelineConfig.builder().staypointRadiusMeters(50).build());
        assertThat(stay.getLongitude()).isEqualTo(30.025);
        assertThat(stay.getLatitude()).isEqualTo(50.005);
        assertThat(state.getActiveExplanation().getNotes()).anyMatch(n -> TimelineReasons.FAVORITE_AREA.equals(n.code()));
    }
    @Test void validatesGeometryAndLegacyBounds() {
        assertThat(shape().toPolygon().getSRID()).isEqualTo(4326);
        assertThat(shape().toPolygon().getInteriorPoint().within(shape().toPolygon())).isTrue();
        assertThat(AreaGeometry.resolve(null, 51., 31., 50., 30.).getArea()).isEqualTo(1.);
        assertThatThrownBy(() -> AreaGeometry.resolve(shape(), 51., 31., 50., 30.)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AreaGeometry.resolve(null, 51., null, 50., 30.)).isInstanceOf(IllegalArgumentException.class);
        for (var geometry : List.of(
                new AreaGeometry("MultiPolygon", shape().getCoordinates()),
                new AreaGeometry("Polygon", List.of(shape().getCoordinates().getFirst(), shape().getCoordinates().getFirst())),
                new AreaGeometry("Polygon", List.of(List.of(List.of(30.,50.), List.of(31.,51.), List.of(30.,51.), List.of(31.,50.), List.of(30.,50.)))),
                new AreaGeometry("Polygon", List.of(List.of(List.of(30.,50.), List.of(31.,50.), List.of(32.,50.), List.of(30.,50.)))),
                new AreaGeometry("Polygon", List.of(List.of(List.of(179.,50.), List.of(-179.,50.), List.of(-179.,51.), List.of(179.,50.)))))) {
            assertThatThrownBy(geometry::toPolygon).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
