package org.github.tess1o.geopulse.streaming.engine;

import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TimelineEventFinalizationEndpointSnapTest {

    private final TimelineEventFinalizationService finalization = new TimelineEventFinalizationService();

    @Test
    void shouldOnlySnapTripStartToFavoriteStayWhenNoPreviousTripIsBetweenThem() {
        Stay originStay = stay("2026-06-25T18:00:00Z", 900, 1L, 49.57054227, 25.57159551);
        Stay destinationStay = stay("2026-06-25T18:50:00Z", 900, 2L, 49.55200000, 25.59600000);

        Trip boatTrip = trip("2026-06-25T18:20:38Z", 598,
                49.56614000, 25.57467600, 49.55513100, 25.58589200);
        Trip walkingTrip = trip("2026-06-25T18:30:36Z", 845,
                49.55513100, 25.58589200, 49.55235000, 25.59486900);

        finalization.snapTripEndpointsToAdjacentFavoriteStays(
                List.<TimelineEvent>of(originStay, boatTrip, walkingTrip, destinationStay));

        assertThat(boatTrip.getStartPoint().getLatitude()).isEqualTo(49.57054227);
        assertThat(boatTrip.getStartPoint().getLongitude()).isEqualTo(25.57159551);
        assertThat(boatTrip.getEndPoint().getLatitude()).isEqualTo(49.55513100);
        assertThat(boatTrip.getEndPoint().getLongitude()).isEqualTo(25.58589200);

        assertThat(walkingTrip.getStartPoint().getLatitude()).isEqualTo(49.55513100);
        assertThat(walkingTrip.getStartPoint().getLongitude()).isEqualTo(25.58589200);
        assertThat(walkingTrip.getEndPoint().getLatitude()).isEqualTo(49.55200000);
        assertThat(walkingTrip.getEndPoint().getLongitude()).isEqualTo(25.59600000);
    }

    private Stay stay(String timestamp, long durationSeconds, Long favoriteId,
                      double latitude, double longitude) {
        return Stay.builder()
                .startTime(Instant.parse(timestamp))
                .duration(Duration.ofSeconds(durationSeconds))
                .favoriteId(favoriteId)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

    private Trip trip(String timestamp, long durationSeconds,
                      double latitude, double longitude, double endLatitude, double endLongitude) {
        return Trip.builder()
                .startTime(Instant.parse(timestamp))
                .duration(Duration.ofSeconds(durationSeconds))
                .startPoint(GPSPoint.builder().timestamp(Instant.parse(timestamp))
                        .latitude(latitude).longitude(longitude).build())
                .endPoint(GPSPoint.builder().timestamp(Instant.parse(timestamp).plusSeconds(durationSeconds))
                        .latitude(endLatitude).longitude(endLongitude).build())
                .build();
    }
}
