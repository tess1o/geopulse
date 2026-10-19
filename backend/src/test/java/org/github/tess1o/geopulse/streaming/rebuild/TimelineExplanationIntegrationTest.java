package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineExplanation;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineReason;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.ISSUE_627_EXPORT;

/**
 * The timeline engine records why it created each stay, trip and data gap as it did. A real day (GitHub issue #627):
 * the night at home, the drive to the office, the office stay, the drive home that ends in a data gap, and the stay
 * at home after it.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineExplanationIntegrationTest {

    @Inject
    TimelineReplayHarness harness;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void everyEventSaysWhyItStartsAndEndsWhereItDoes() {
        UUID userId = harness.importExport(TimelineReplayHarness.readResource(ISSUE_627_EXPORT), "explanation");

        List<TimelineStayEntity> stays = QuarkusTransaction.requiringNew().call(() -> entityManager.createQuery(
                        "select s from TimelineStayEntity s where s.user.id = :userId order by s.timestamp",
                        TimelineStayEntity.class)
                .setParameter("userId", userId).getResultList());
        List<TimelineTripEntity> trips = QuarkusTransaction.requiringNew().call(() -> entityManager.createQuery(
                        "select t from TimelineTripEntity t where t.user.id = :userId order by t.timestamp",
                        TimelineTripEntity.class)
                .setParameter("userId", userId).getResultList());
        List<TimelineDataGapEntity> gaps = QuarkusTransaction.requiringNew().call(() -> entityManager.createQuery(
                        "select g from TimelineDataGapEntity g where g.user.id = :userId order by g.startTime",
                        TimelineDataGapEntity.class)
                .setParameter("userId", userId).getResultList());

        // The first stay begins with the first point there is, or where the first few points came to a stop, and is
        // confirmed as a stay
        TimelineExplanation night = explanation(stays.getFirst().getExplanation());
        assertThat(night.getStart().code()).isIn("FIRST_POINT", "ARRIVAL");
        assertThat(night.getNotes()).extracting(TimelineReason::code).contains("CONFIRMED");
        assertThat(night.getEnd().code()).isEqualTo("MOVED_AWAY");
        assertThat(((Number) night.getEnd().params().get("distanceMeters")).doubleValue())
                .isGreaterThan(((Number) night.getEnd().params().get("radiusMeters")).doubleValue());

        // The morning drive leaves the stay and ends where the office stay starts: both name the same stop
        TimelineTripEntity morningDrive = tripStartingAfter(trips, Instant.parse("2026-10-08T00:00:00Z"));
        TimelineExplanation drive = explanation(morningDrive.getExplanation());
        assertThat(drive.getStart().code()).isEqualTo("LEFT_STAY");
        assertThat(drive.getEnd().code()).isEqualTo("ARRIVAL");
        TimelineStayEntity office = stayStartingAt(stays, morningDrive.getTimestamp().plusSeconds(morningDrive.getTripDuration()));
        TimelineExplanation officeExplanation = explanation(office.getExplanation());
        assertThat(officeExplanation.getStart()).isEqualTo(drive.getEnd());

        // The data gap says how long there were no points and what counts as a gap
        TimelineDataGapEntity gap = gaps.stream()
                .filter(candidate -> candidate.getStartTime().equals(Instant.parse("2026-10-08T11:59:00Z")))
                .findFirst().orElseThrow();
        TimelineExplanation gapExplanation = explanation(gap.getExplanation());
        assertThat(gapExplanation.getStart().code()).isEqualTo("NO_POINTS");
        assertThat(gapExplanation.getStart().params()).containsKeys("gapSeconds", "thresholdSeconds");

        // The drive home ends at that gap, and the stay after it begins after a gap
        TimelineTripEntity driveHome = tripStartingAfter(trips, Instant.parse("2026-10-08T11:00:00Z"));
        assertThat(explanation(driveHome.getExplanation()).getEnd().code()).isEqualTo("DATA_GAP");
        TimelineStayEntity evening = stays.getLast();
        TimelineExplanation eveningExplanation = explanation(evening.getExplanation());
        assertThat(eveningExplanation.getStart().code()).isEqualTo("AFTER_DATA_GAP");
        assertThat(eveningExplanation.getEnd().code()).isEqualTo("LAST_POINT");

        // The gap still open after the last point has its own explanation
        assertThat(explanation(gaps.getLast().getExplanation()).getStart().code()).isEqualTo("NO_POINTS_SINCE_LAST");
    }

    private static TimelineExplanation explanation(Map<String, Object> stored) {
        assertThat(stored).as("explanation").isNotNull();
        return TimelineExplanation.fromMap(stored);
    }

    private static TimelineTripEntity tripStartingAfter(List<TimelineTripEntity> trips, Instant after) {
        return trips.stream().filter(trip -> trip.getTimestamp().isAfter(after)).findFirst().orElseThrow();
    }

    private static TimelineStayEntity stayStartingAt(List<TimelineStayEntity> stays, Instant start) {
        return stays.stream().filter(stay -> stay.getTimestamp().equals(start)).findFirst().orElseThrow();
    }
}
