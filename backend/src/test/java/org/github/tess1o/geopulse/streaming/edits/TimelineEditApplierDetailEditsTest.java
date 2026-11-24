package org.github.tess1o.geopulse.streaming.edits;

import org.github.tess1o.geopulse.streaming.model.domain.DataGap;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.github.tess1o.geopulse.streaming.model.domain.Stay;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEvent;
import org.github.tess1o.geopulse.streaming.model.domain.Trip;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.CorrectionTestPayloads.command;

/**
 * Detail edits (movement type, place, visit name) set fields of one event, so the fold skips the whole-timeline
 * passes for them. Random sequences of detail and structural edits must fold exactly as without that shortcut:
 * the same events, effects, unapplied edits and recalculated trips.
 */
@Tag("unit")
class TimelineEditApplierDetailEditsTest {

    private static final Instant START = Instant.parse("2024-03-01T06:00:00Z");
    private static final int DAYS = 40;
    private static final int EDITS = 120;

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233})
    void detailEditsFoldExactlyAsWithoutTheShortcut(long seed) {
        List<TimelineEvent> events = timeline();
        List<TimelineEditCommand> commands = commands(events, new Random(seed));

        Folded fast = fold(events, commands, true);
        Folded full = fold(events, commands, false);

        assertThat(fast.result().events()).isEqualTo(full.result().events());
        assertThat(fast.result().effects()).isEqualTo(full.result().effects());
        assertThat(fast.result().unappliedEditIds()).isEqualTo(full.result().unappliedEditIds());
        assertThat(fast.recalculated()).isEqualTo(full.recalculated());
        // The sequences exercise both paths and every outcome
        assertThat(full.result().appliedEditIds()).hasSizeGreaterThan(EDITS / 2);
        assertThat(full.result().unappliedEditIds()).isNotEmpty();
    }

    private record Folded(TimelineEditResult result, List<List<Instant>> recalculated) {
    }

    private static Folded fold(List<TimelineEvent> events, List<TimelineEditCommand> commands, boolean shortcut) {
        TimelineEditApplier applier = new TimelineEditApplier();
        applier.detailEditShortcut = shortcut;
        List<List<Instant>> recalculated = new ArrayList<>();
        TimelineEditResult result = applier.apply(events, commands,
                trip -> recalculated.add(List.of(trip.getStartTime(), trip.getEndTime())));
        return new Folded(result, recalculated);
    }

    /** A stay and a trip every day, a data gap in the middle. */
    private static List<TimelineEvent> timeline() {
        List<TimelineEvent> events = new ArrayList<>();
        for (int day = 0; day < DAYS; day++) {
            Instant stayStart = START.plus(Duration.ofHours(3L * day));
            if (day == DAYS / 2) {
                events.add(DataGap.fromTimeRange(stayStart, stayStart.plus(Duration.ofHours(3))));
                continue;
            }
            events.add(Stay.builder().startTime(stayStart).duration(Duration.ofMinutes(150))
                    .locationName("Place " + day).latitude(50 + day * 0.001).longitude(30).build());
            Instant tripStart = stayStart.plus(Duration.ofMinutes(150));
            events.add(Trip.builder().startTime(tripStart).duration(Duration.ofMinutes(30)).tripType(TripType.CAR)
                    .distanceMeters(1200)
                    .startPoint(GPSPoint.builder().timestamp(tripStart).latitude(50 + day * 0.001).longitude(30).build())
                    .endPoint(GPSPoint.builder().timestamp(tripStart.plus(Duration.ofMinutes(30)))
                            .latitude(50 + (day + 1) * 0.001).longitude(30).build())
                    .build());
        }
        return events;
    }

    private static List<TimelineEditCommand> commands(List<TimelineEvent> events, Random random) {
        List<TimelineEditCommand> commands = new ArrayList<>();
        for (int i = 0; i < EDITS; i++) {
            long id = i + 1;
            TimelineEvent target = events.get(random.nextInt(events.size()));
            Instant start = target.getStartTime();
            Instant end = target.getEndTime();
            if (random.nextInt(10) == 0) {
                // A stale anchor, as after a regeneration moved the event: matched by overlap, or dormant
                start = start.plus(Duration.ofMinutes(random.nextInt(240) - 120));
                end = start.plus(Duration.ofMinutes(20));
            }
            int kind = random.nextInt(10);
            commands.add(switch (kind) {
                case 0, 1, 2 -> command(id, id, TimelineEditKind.SET_MOVEMENT_TYPE, start, end,
                        Map.of("movementType", random.nextBoolean() ? "WALK" : "BICYCLE"));
                case 3 -> command(id, id, TimelineEditKind.SET_PLACE, start, end,
                        Map.of("latitude", 50.5, "longitude", 30.5, "locationName", "Renamed " + i));
                case 4 -> command(id, id, TimelineEditKind.SET_VISIT_NAME, start, end, Map.of("visitName", "Visit " + i));
                case 5 -> command(id, id, TimelineEditKind.SET_TIMES, start, end, Map.of(
                        "start", start.plus(Duration.ofMinutes(5)).toString(),
                        "end", end.minus(Duration.ofMinutes(5)).toString()));
                case 6 -> command(id, id, TimelineEditKind.MERGE, start, end,
                        Map.of("direction", random.nextBoolean() ? "NEXT" : "PREVIOUS"));
                case 7 -> command(id, id, TimelineEditKind.REMOVE_EVENT, start, end,
                        Map.of("resolution", List.of("GAP", "EXTEND_PREVIOUS", "EXTEND_NEXT").get(random.nextInt(3))));
                default -> command(id, id, TimelineEditKind.FORCE_STAY,
                        start.plus(Duration.between(start, end).dividedBy(3)),
                        start.plus(Duration.between(start, end).dividedBy(3).multipliedBy(2)),
                        Map.of("latitude", 50.2, "longitude", 30.2, "locationName", "Stop " + i));
            });
        }
        return commands;
    }
}
