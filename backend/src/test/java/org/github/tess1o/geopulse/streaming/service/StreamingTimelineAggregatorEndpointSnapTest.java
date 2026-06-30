package org.github.tess1o.geopulse.streaming.service;

import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class StreamingTimelineAggregatorEndpointSnapTest {

    private final StreamingTimelineAggregator aggregator = new StreamingTimelineAggregator();

    @Test
    void shouldOnlySnapTripStartToFavoriteStayWhenNoPreviousTripIsBetweenThem() {
        TimelineStayLocationDTO originStay = stay(
                "2026-06-25T18:00:00Z",
                900,
                1L,
                49.57054227,
                25.57159551
        );
        TimelineStayLocationDTO destinationStay = stay(
                "2026-06-25T18:50:00Z",
                900,
                2L,
                49.55200000,
                25.59600000
        );

        TimelineTripDTO boatTrip = trip(
                "2026-06-25T18:20:38Z",
                598,
                49.56614000,
                25.57467600,
                49.55513100,
                25.58589200
        );
        TimelineTripDTO walkingTrip = trip(
                "2026-06-25T18:30:36Z",
                845,
                49.55513100,
                25.58589200,
                49.55235000,
                25.59486900
        );

        aggregator.snapTripEndpointsToAdjacentFavoriteStays(
                List.of(boatTrip, walkingTrip),
                List.of(originStay, destinationStay)
        );

        assertThat(boatTrip.getLatitude()).isEqualTo(49.57054227);
        assertThat(boatTrip.getLongitude()).isEqualTo(25.57159551);
        assertThat(boatTrip.getEndLatitude()).isEqualTo(49.55513100);
        assertThat(boatTrip.getEndLongitude()).isEqualTo(25.58589200);

        assertThat(walkingTrip.getLatitude()).isEqualTo(49.55513100);
        assertThat(walkingTrip.getLongitude()).isEqualTo(25.58589200);
        assertThat(walkingTrip.getEndLatitude()).isEqualTo(49.55200000);
        assertThat(walkingTrip.getEndLongitude()).isEqualTo(25.59600000);
    }

    private TimelineStayLocationDTO stay(String timestamp,
                                         long durationSeconds,
                                         Long favoriteId,
                                         double latitude,
                                         double longitude) {
        return TimelineStayLocationDTO.builder()
                .timestamp(Instant.parse(timestamp))
                .stayDuration(durationSeconds)
                .favoriteId(favoriteId)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

    private TimelineTripDTO trip(String timestamp,
                                 long durationSeconds,
                                 double latitude,
                                 double longitude,
                                 double endLatitude,
                                 double endLongitude) {
        return TimelineTripDTO.builder()
                .timestamp(Instant.parse(timestamp))
                .tripDuration(durationSeconds)
                .latitude(latitude)
                .longitude(longitude)
                .endLatitude(endLatitude)
                .endLongitude(endLongitude)
                .build();
    }
}
