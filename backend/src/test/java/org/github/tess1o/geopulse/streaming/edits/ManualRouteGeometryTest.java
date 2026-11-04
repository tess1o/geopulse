package org.github.tess1o.geopulse.streaming.edits;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

@Tag("unit")
class ManualRouteGeometryTest {
    @Test
    void readsStoredCoordinatesUsingTheSuppliedMapperConfiguration() {
        var mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        var route = ManualRouteGeometry.read(List.of(
                Map.of("latitude", 0, "longitude", 0, "extra", true),
                Map.of("latitude", 0, "longitude", 1)), mapper);

        assertThat(route).containsExactly(point(0, 0), point(0, 1));
        assertThat(ManualRouteGeometry.distance(route)).isCloseTo(111_195, within(1.0));
    }

    @Test
    void rejectsMissingOutOfRangeAndNonFiniteCoordinates() {
        for (var invalid : Arrays.asList(null, new TripReconstructionWaypointDto(null, 0.0),
                new TripReconstructionWaypointDto(0.0, null), point(91, 0), point(0, 181),
                point(-91, 0), point(0, -181), point(Double.NaN, 0),
                point(0, Double.NaN), point(Double.POSITIVE_INFINITY, 0),
                point(0, Double.NEGATIVE_INFINITY))) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    ManualRouteGeometry.validate(Arrays.asList(point(0, 0), invalid)));
        }
    }

    @Test
    void enforcesRouteSizeAndAcceptsBoundaryCoordinates() {
        assertThatIllegalArgumentException().isThrownBy(() -> ManualRouteGeometry.validate(null));
        assertThatIllegalArgumentException().isThrownBy(() -> ManualRouteGeometry.validate(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> ManualRouteGeometry.validate(List.of(point(0, 0))));
        assertThatIllegalArgumentException().isThrownBy(() ->
                ManualRouteGeometry.validate(Collections.nCopies(1001, point(0, 0))));
        ManualRouteGeometry.validate(Collections.nCopies(1000, point(0, 0)));
        ManualRouteGeometry.validate(List.of(point(-90, -180), point(90, 180)));
        assertThat(ManualRouteGeometry.distance(List.of(point(0, 0), point(0, 0)))).isZero();
    }

    private static TripReconstructionWaypointDto point(double latitude, double longitude) {
        return new TripReconstructionWaypointDto(latitude, longitude);
    }
}
