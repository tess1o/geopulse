package org.github.tess1o.geopulse.streaming.service.explanation;

import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineExplanation;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReason;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReasons;
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEventExplanationDetailsDTO.Point;
import org.github.tess1o.geopulse.streaming.service.explanation.TimelineExplanationPointClassifier.Kind;
import org.github.tess1o.geopulse.streaming.service.explanation.TimelineExplanationPointClassifier.RawPoint;
import org.github.tess1o.geopulse.streaming.service.explanation.TimelineExplanationPointClassifier.Result;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TimelineExplanationPointClassifierTest {

    private static final Instant T0 = Instant.parse("2026-10-08T17:00:00Z");
    private static final double LAT = 49.8400;
    private static final double LON = 24.0300;
    /** About 111 m of latitude per 0.001 degree. */
    private static final double FAR = 0.0016;

    private static final TimelineConfig CONFIG = TimelineConfig.builder()
            .useVelocityAccuracy(true)
            .staypointMaxAccuracyThreshold(60.0)
            .staypointRadiusMeters(50)
            .staypointMinDurationMinutes(7)
            .dataGapThresholdSeconds(3600)
            .build();

    @Test
    void staySaysWhichPointsStartedItWhichEndedItAndWhichWereLeftOut() {
        List<RawPoint> points = new ArrayList<>();
        points.add(point(-10, LAT - FAR, LON, 8.0));        // before: still on the way
        for (int minute = 0; minute <= 30; minute += 2) {
            points.add(point(minute, LAT, LON, 5.0));
        }
        points.add(point(11, LAT + FAR, LON, 150.0));       // an inaccurate fix far away, left out
        points.add(point(31, LAT + FAR, LON, 8.0));          // moved away
        points.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));

        TimelineExplanation explanation = new TimelineExplanation(
                TimelineReason.of(TimelineReasons.ARRIVAL, "stopSeconds", 240, "points", 3, "stopSpeedKmh", 9.0),
                List.of(TimelineReason.of(TimelineReasons.CONFIRMED, "radiusMeters", 50, "minMinutes", 7)),
                TimelineReason.of(TimelineReasons.MOVED_AWAY, "distanceMeters", 178, "radiusMeters", 50));

        Result result = TimelineExplanationPointClassifier.classify(Kind.STAY, T0, T0.plusSeconds(30 * 60),
                explanation, CONFIG, points, null);

        assertThat(withReason(result, TimelineReasons.ARRIVAL)).extracting(Point::timestamp)
                .containsExactly(at(0), at(2), at(4));
        assertThat(withReason(result, TimelineReasons.CONFIRMED)).extracting(Point::timestamp)
                .containsExactly(at(8));
        Point movedAway = withReason(result, TimelineReasons.MOVED_AWAY).getFirst();
        assertThat(movedAway.timestamp()).isEqualTo(at(31));
        assertThat(movedAway.part()).isEqualTo("AFTER");
        assertThat(movedAway.outsideRadius()).isTrue();

        Point leftOut = result.points().stream().filter(point -> !point.used()).findFirst().orElseThrow();
        assertThat(leftOut.timestamp()).isEqualTo(at(11));
        assertThat(leftOut.part()).isEqualTo("EVENT");
        assertThat(leftOut.reasons()).isEmpty();
        assertThat(result.filtered().inEvent()).isEqualTo(1);
        assertThat(result.filtered().thresholdMeters()).isEqualTo(60.0);

        // The centre is the average of the used points only, so the far inaccurate fix doesn't move it
        assertThat(result.centre().latitude()).isCloseTo(LAT, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(result.points().getFirst().part()).isEqualTo("BEFORE");
    }

    @Test
    void dataGapEdgesAreTheLastPointBeforeAndTheFirstAfterEvenWithLeftOutPointsInside() {
        List<RawPoint> points = List.of(
                point(0, LAT, LON, 5.0),
                point(30, LAT, LON, 400.0),
                point(60, LAT, LON, 400.0),
                point(120, LAT + FAR, LON, 5.0));
        TimelineExplanation explanation = new TimelineExplanation(
                TimelineReason.of(TimelineReasons.NO_POINTS, "gapSeconds", 7200, "thresholdSeconds", 3600),
                List.of(), null);

        Result result = TimelineExplanationPointClassifier.classify(Kind.DATA_GAP, at(0), at(120), explanation,
                CONFIG, points, null);

        assertThat(result.points()).extracting(Point::part).containsExactly("BEFORE", "EVENT", "EVENT", "AFTER");
        assertThat(withReason(result, TimelineReasons.NO_POINTS)).extracting(Point::timestamp)
                .containsExactly(at(0), at(120));
        assertThat(result.filtered().inEvent()).isEqualTo(2);
        assertThat(result.centre()).isNull();
    }

    @Test
    void shortStopMarksTheStopAndTripArrivalMarksTheStopAfterIt() {
        List<RawPoint> points = List.of(
                point(0, LAT, LON, 5.0),
                point(2, LAT, LON, 5.0),
                point(4, LAT + FAR, LON, 5.0),
                point(10, LAT + 2 * FAR, LON, 5.0),
                point(12, LAT + 2 * FAR, LON, 5.0),
                point(14, LAT + 2 * FAR, LON, 5.0));
        TimelineExplanation explanation = new TimelineExplanation(
                TimelineReason.of(TimelineReasons.SHORT_STOP, "stopSeconds", 120, "minMinutes", 7),
                List.of(),
                TimelineReason.of(TimelineReasons.ARRIVAL, "stopSeconds", 240, "points", 3, "stopSpeedKmh", 9.0));

        Result result = TimelineExplanationPointClassifier.classify(Kind.TRIP, at(0), at(10), explanation, CONFIG,
                points, null);

        assertThat(withReason(result, TimelineReasons.SHORT_STOP)).extracting(Point::timestamp)
                .containsExactly(at(0), at(2));
        assertThat(withReason(result, TimelineReasons.ARRIVAL)).extracting(Point::timestamp)
                .containsExactly(at(10), at(12), at(14));
    }

    @Test
    void settingsChangedSinceTheTimelineWasBuiltAreReported() {
        TimelineExplanation explanation = new TimelineExplanation(
                TimelineReason.of(TimelineReasons.FIRST_POINT),
                List.of(TimelineReason.of(TimelineReasons.CONFIRMED, "radiusMeters", 80, "minMinutes", 7)),
                TimelineReason.of(TimelineReasons.MOVED_AWAY, "distanceMeters", 120, "radiusMeters", 80));

        assertThat(TimelineExplanationPointClassifier.settingsChanged(explanation, CONFIG))
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.setting()).isEqualTo("staypointRadiusMeters");
                    assertThat(change.builtWith().intValue()).isEqualTo(80);
                    assertThat(change.current().intValue()).isEqualTo(50);
                });
    }

    @Test
    void longEventsKeepEveryLeftOutAndDecidingPoint() {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < TimelineEventExplanationService.MAX_EVENT_POINTS * 2; i++) {
            boolean leftOut = i % 500 == 7;
            List<String> reasons = i == 3 ? List.of(TimelineReasons.ARRIVAL) : List.of();
            points.add(new Point(T0.plusSeconds(i), LAT, LON, leftOut ? 300.0 : 5.0, 0.0, "EVENT", !leftOut,
                    null, null, reasons));
        }
        points.add(new Point(T0.plusSeconds(100_000), LAT, LON, 5.0, 0.0, "AFTER", true, null, null,
                List.of(TimelineReasons.MOVED_AWAY)));

        List<Point> thinned = TimelineEventExplanationService.thin(points);

        assertThat(thinned.stream().filter(point -> "EVENT".equals(point.part())).count())
                .isLessThanOrEqualTo(TimelineEventExplanationService.MAX_EVENT_POINTS);
        assertThat(thinned.stream().filter(point -> !point.used()).count())
                .isEqualTo(points.stream().filter(point -> !point.used()).count());
        assertThat(thinned).anyMatch(point -> point.reasons().contains(TimelineReasons.ARRIVAL));
        assertThat(thinned.getLast().reasons()).containsExactly(TimelineReasons.MOVED_AWAY);
    }

    private static List<Point> withReason(Result result, String code) {
        return result.points().stream().filter(point -> point.reasons().contains(code)).toList();
    }

    private static RawPoint point(int minute, double lat, double lon, Double accuracy) {
        return new RawPoint(at(minute), lat, lon, accuracy, 0.0);
    }

    private static Instant at(int minute) {
        return T0.plusSeconds(minute * 60L);
    }
}
