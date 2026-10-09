package org.github.tess1o.geopulse.streaming.service;

import org.github.tess1o.geopulse.geocoding.dto.LocationTimezoneDTO;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineDataGapDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineStayLocationDTO;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineTripDTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TimelineLocationTimezoneEnricherTest {

    private static final Instant T0 = Instant.parse("2025-07-10T08:00:00Z");

    @Test
    void gapTakesZoneOfTripEndBeforeAndStayStartAfter() {
        MovementTimelineDTO timeline = new MovementTimelineDTO(UUID.randomUUID());
        timeline.getTrips().add(TimelineTripDTO.builder()
                .timestamp(T0)
                .tripDuration(3600)
                .startLocationTimezone(zone("Europe/Kyiv"))
                .endLocationTimezone(zone("Europe/Warsaw"))
                .build());
        timeline.getStays().add(TimelineStayLocationDTO.builder()
                .timestamp(T0.plusSeconds(5 * 3600))
                .stayDuration(3600)
                .locationTimezone(zone("Europe/Sofia"))
                .build());
        TimelineDataGapDTO gap = new TimelineDataGapDTO(1L, T0.plusSeconds(3600), T0.plusSeconds(5 * 3600));
        timeline.getDataGaps().add(gap);

        TimelineLocationTimezoneEnricher.assignDataGapTimezones(timeline);

        assertThat(gap.getStartLocationTimezone().getTimezone()).isEqualTo("Europe/Warsaw");
        assertThat(gap.getEndLocationTimezone().getTimezone()).isEqualTo("Europe/Sofia");
    }

    @Test
    void gapAtRangeEdgesHasNoNeighbourZone() {
        MovementTimelineDTO timeline = new MovementTimelineDTO(UUID.randomUUID());
        timeline.getStays().add(TimelineStayLocationDTO.builder()
                .timestamp(T0.plusSeconds(3600))
                .stayDuration(600)
                .locationTimezone(zone("Europe/Kyiv"))
                .build());
        TimelineDataGapDTO leading = new TimelineDataGapDTO(1L, T0, T0.plusSeconds(3600));
        TimelineDataGapDTO trailing = new TimelineDataGapDTO(2L, T0.plusSeconds(4200), T0.plusSeconds(9000));
        timeline.getDataGaps().add(leading);
        timeline.getDataGaps().add(trailing);

        TimelineLocationTimezoneEnricher.assignDataGapTimezones(timeline);

        assertThat(leading.getStartLocationTimezone()).isNull();
        assertThat(leading.getEndLocationTimezone().getTimezone()).isEqualTo("Europe/Kyiv");
        assertThat(trailing.getStartLocationTimezone().getTimezone()).isEqualTo("Europe/Kyiv");
        assertThat(trailing.getEndLocationTimezone()).isNull();
    }

    private static LocationTimezoneDTO zone(String timezone) {
        return LocationTimezoneDTO.builder().timezone(timezone).status(LocationTimezoneDTO.Status.RESOLVED).build();
    }
}
