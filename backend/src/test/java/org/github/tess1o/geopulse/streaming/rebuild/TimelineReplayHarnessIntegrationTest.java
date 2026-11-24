package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.assertj.core.groups.Tuple;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineReplayHarnessIntegrationTest {

    @Inject TimelineReplayHarness harness;
    @Inject EntityManager entityManager;

    @Test
    void seededHistoryMatchesRealIngestionAcrossPersistenceBatches() {
        GeocodingTestMocks.install();
        var recording = SyntheticDays.generate(11, LocalDate.of(2026, 9, 3), 4);
        assertThat(recording).hasSizeGreaterThan(500);
        recording.getFirst().setExt(new LinkedHashMap<>(Map.of("fixture", "telemetry")));
        UUID seeded = harness.createUserWithFavoriteAreas("seeded-history", SyntheticDays.NAMED_PLACES);
        UUID ingested = harness.createUserWithFavoriteAreas("ingested-history", SyntheticDays.NAMED_PLACES);
        Instant before = Instant.now();

        harness.seedRecording(seeded, recording);
        var upload = new ArrayList<>(recording);
        // Exact duplicates must still be rejected after ingest clears the persistence context.
        upload.add(recording.getFirst());
        harness.ingest(ingested, upload);

        assertThat(harness.pendingRebuild(seeded).orElseThrow().rebuildFrom())
                .isEqualTo(harness.pendingRebuild(ingested).orElseThrow().rebuildFrom());
        var stored = points(seeded);
        assertThat(stored).hasSize(recording.size()).extracting(this::values)
                .containsExactlyElementsOf(points(ingested).stream().map(this::values).toList());
        assertThat(stored).allSatisfy(point -> assertThat(point.getCreatedAt()).isAfterOrEqualTo(before));
        assertThat(stored.getFirst().getTelemetry()).containsEntry("fixture", "telemetry");

        harness.fullRebuild(seeded);
        harness.fullRebuild(ingested);
        assertThat(harness.pendingRebuild(seeded)).isEmpty();
        assertThat(harness.pendingRebuild(ingested)).isEmpty();
        assertThat(harness.snapshot(seeded).contentAcrossUsers())
                .containsExactlyElementsOf(harness.snapshot(ingested).contentAcrossUsers());
    }

    private List<GpsPointEntity> points(UUID userId) {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.createQuery(
                        "from GpsPointEntity where user.id = :userId order by timestamp", GpsPointEntity.class)
                .setParameter("userId", userId).getResultList());
    }

    private Tuple values(GpsPointEntity point) {
        return tuple(point.getDeviceId(), point.getTimestamp(), point.getCoordinates().toText(),
                point.getAccuracy(), point.getBattery(), point.getVelocity(), point.getAltitude(),
                point.getSourceType(), point.getTelemetry());
    }
}
