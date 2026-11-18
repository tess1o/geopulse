package org.github.tess1o.geopulse.streaming.edits;

import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.CorrectionTestPayloads.command;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * A full regeneration folds every active correction over every generated event. On a multi-year account that is tens
 * of thousands of events and hundreds to thousands of corrections; the fold must stay linear in both.
 */
@Tag("unit")
class TimelineEditApplierScaleTest {

    private static final Instant START = Instant.parse("2021-01-01T00:00:00Z");
    private static final int DAYS = 20_000;
    private static final int CORRECTIONS = 1_000;

    private final TimelineEditApplier applier = new TimelineEditApplier();

    @Test
    void foldsAThousandCorrectionsOverAMultiYearTimelineQuickly() {
        List<TimelineEvent> events = new ArrayList<>();
        for (int day = 0; day < DAYS; day++) {
            Instant stayStart = START.plus(Duration.ofHours(2L * day));
            events.add(Stay.builder().startTime(stayStart).duration(Duration.ofMinutes(90))
                    .locationName("Home").latitude(49.84).longitude(24.03).build());
            Instant tripStart = stayStart.plus(Duration.ofMinutes(90));
            events.add(Trip.builder().startTime(tripStart).duration(Duration.ofMinutes(30)).tripType(TripType.CAR)
                    .startPoint(GPSPoint.builder().timestamp(tripStart).latitude(49.84).longitude(24.03).build())
                    .endPoint(GPSPoint.builder().timestamp(tripStart.plus(Duration.ofMinutes(30)))
                            .latitude(49.83).longitude(24.07).build())
                    .build());
        }
        List<TimelineEditCommand> commands = new ArrayList<>();
        int step = DAYS / CORRECTIONS;
        for (int i = 0; i < CORRECTIONS; i++) {
            long id = i + 1;
            Instant stayStart = START.plus(Duration.ofHours(2L * i * step));
            Instant tripStart = stayStart.plus(Duration.ofMinutes(90));
            commands.add(switch (i % 3) {
                case 0 -> command(id, id, TimelineEditKind.SET_VISIT_NAME, stayStart, stayStart.plus(Duration.ofMinutes(90)),
                        Map.of("visitName", "Visit " + i));
                case 1 -> command(id, id, TimelineEditKind.SET_MOVEMENT_TYPE, tripStart, tripStart.plus(Duration.ofMinutes(30)),
                        Map.of("movementType", "WALK"));
                default -> command(id, id, TimelineEditKind.FORCE_STAY, tripStart.plus(Duration.ofMinutes(10)),
                        tripStart.plus(Duration.ofMinutes(20)),
                        Map.of("latitude", 49.835, "longitude", 24.05, "locationName", "Bakery"));
            });
        }

        TimelineEditResult result = assertTimeoutPreemptively(Duration.ofSeconds(30),
                () -> applier.apply(events, commands));

        assertThat(result.unappliedEditIds()).isEmpty();
        assertThat(result.appliedEditIds()).hasSize(CORRECTIONS);
        // Each fill splits its trip in two around the inserted stay.
        assertThat(result.events()).hasSize(2 * DAYS + 2 * (CORRECTIONS / 3));
    }
}
