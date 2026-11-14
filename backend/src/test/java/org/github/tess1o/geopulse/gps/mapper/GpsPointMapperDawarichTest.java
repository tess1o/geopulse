package org.github.tess1o.geopulse.gps.mapper;

import org.github.tess1o.geopulse.gps.integrations.dawarich.model.point.DawarichGeometry;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.point.DawarichLocation;
import org.github.tess1o.geopulse.gps.integrations.dawarich.model.point.DawarichProperties;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class GpsPointMapperDawarichTest {

    private final GpsPointMapper mapper = new GpsPointMapper();

    @Test
    void toEntity_dawarichPointWithoutAltitude_keepsAltitudeNull() {
        DawarichLocation location = location(null, 8.0, 4.0);

        GpsPointEntity entity = mapper.toEntity(location, new UserEntity(), GpsSourceType.DAWARICH);

        assertThat(entity.getAltitude()).isNull();
        assertThat(entity.getCoordinates().getX()).isEqualTo(24.7111);
        assertThat(entity.getCoordinates().getY()).isEqualTo(48.9226);
    }

    @Test
    void toEntity_dawarichPointWithAltitude_roundsIt() {
        DawarichLocation location = location(245.4, 8.0, 4.0);

        GpsPointEntity entity = mapper.toEntity(location, new UserEntity(), GpsSourceType.DAWARICH);

        assertThat(entity.getAltitude()).isEqualTo(245.0);
    }

    @Test
    void toEntity_dawarichPoint_usesHorizontalAccuracyAsPositionAccuracy() {
        // vertical_accuracy is the altitude's precision; iOS reports -1 when it has no altitude.
        DawarichLocation location = location(245.0, 8.0, -1.0);

        GpsPointEntity entity = mapper.toEntity(location, new UserEntity(), GpsSourceType.DAWARICH);

        assertThat(entity.getAccuracy()).isEqualTo(8.0);
    }

    @Test
    void toEntity_dawarichPointWithoutHorizontalAccuracy_keepsAccuracyNull() {
        DawarichLocation location = location(245.0, null, 4.0);

        GpsPointEntity entity = mapper.toEntity(location, new UserEntity(), GpsSourceType.DAWARICH);

        assertThat(entity.getAccuracy()).isNull();
    }

    private static DawarichLocation location(Double altitude, Double horizontalAccuracy, Double verticalAccuracy) {
        DawarichProperties properties = new DawarichProperties();
        properties.setTimestamp(Instant.parse("2025-10-09T06:06:40Z"));
        properties.setAltitude(altitude);
        properties.setSpeed(1.25);
        properties.setHorizontalAccuracy(horizontalAccuracy);
        properties.setVerticalAccuracy(verticalAccuracy);
        properties.setDeviceId("pixel-8");
        return new DawarichLocation("Feature", new DawarichGeometry("Point", List.of(24.7111, 48.9226)), properties);
    }
}
