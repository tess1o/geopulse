package org.github.tess1o.geopulse.streaming.rebuild;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.export.model.DebugExportRequest;
import org.github.tess1o.geopulse.export.service.DebugExportService;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.importdata.model.DebugImportReport;
import org.github.tess1o.geopulse.importdata.model.DebugImportRequest;
import org.github.tess1o.geopulse.importdata.service.DebugImportService;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineGenerationService;
import org.github.tess1o.geopulse.streaming.service.TripMovementTypeOverrideService;
import org.github.tess1o.geopulse.streaming.service.trips.GpsPointEnvironmentService;
import org.github.tess1o.geopulse.testsupport.GeocodingTestMocks;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.github.tess1o.geopulse.streaming.rebuild.TimelineReplayHarness.ISSUE_627_EXPORT;

/**
 * A debug export loaded on another server must regenerate the timeline the user saw: same stays, trips and gaps,
 * including manual overrides and the place identities stays merge by, with coordinates moved east only.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class DebugExportRoundTripIntegrationTest {

    /** The day of issue #627: office stay, drive home, data gap, home stay. */
    private static final Instant DAY_START = Instant.parse("2026-10-08T00:00:00Z");
    private static final Instant DAY_END = Instant.parse("2026-10-08T23:59:59Z");
    private static final double LONGITUDE_SHIFT = 41.123456;

    @Inject
    TimelineReplayHarness harness;

    @Inject
    DebugExportService debugExportService;

    @Inject
    TripMovementTypeOverrideService tripMovementTypeOverrideService;

    @Inject
    StreamingTimelineGenerationService timelineGenerationService;

    @Inject
    EntityManager entityManager;

    @Inject
    DebugImportService debugImportService;

    @Inject
    GpsPointEnvironmentService gpsPointEnvironmentService;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        GeocodingTestMocks.install();
    }

    @Test
    void importedExportRegeneratesTheExportedTimeline() throws Exception {
        UUID source = harness.importExport(TimelineReplayHarness.readResource(ISSUE_627_EXPORT), "debug-rt-source");
        TimelineTripEntity firstTrip = firstTripFrom(source, DAY_START);
        TripType manualType = "BICYCLE".equals(firstTrip.getMovementType()) ? TripType.WALK : TripType.BICYCLE;
        QuarkusTransaction.requiringNew().run(() ->
                tripMovementTypeOverrideService.setManualMovementType(source, firstTrip.getId(), manualType));
        timelineGenerationService.regenerateFullTimeline(source);

        setTimezone(source, "Europe/Kyiv");
        Instant issueTime = Instant.parse("2026-10-08T12:30:00Z");
        byte[] export = export(source, DAY_START, DAY_END, "Drive home shows as one long stay", issueTime);
        UUID copy = harness.createUser("debug-rt-copy");
        DebugImportReport report = debugImportService.importDebugData(copy, export, new DebugImportRequest(true, true));

        harness.assertMatchesExportedTimeline(copy, export);
        assertThat(report.comparison().available()).isTrue();
        assertThat(report.comparison().matches()).isTrue();
        assertThat(report.comparison().rows()).isNotEmpty()
                .allSatisfy(row -> assertThat(row.status()).isEqualTo("MATCH"));
        assertThat(report.warnings()).extracting(DebugImportReport.Warning::code).doesNotContain("TIMELINE_DIFFERS");
        assertThat(report.issue().description()).isEqualTo("Drive home shows as one long stay");
        assertThat(report.issue().time()).isEqualTo(issueTime);
        assertThat(report.requestedRange().start()).isEqualTo(DAY_START);
        assertThat(report.timezoneApplied()).isTrue();
        assertThat(timezone(copy)).isEqualTo("Europe/Kyiv");
        assertThat(report.timelineStatus()).containsKey("last_built_at");
        assertThat(harness.structure(copy, export)).containsExactlyElementsOf(harness.structure(source, export));
        assertThat(harness.structure(copy, export)).isNotEmpty();

        // The stay the day starts in began the afternoon before, on coming home from a 13-minute stop 400 m away:
        // the export reaches back to its start, so the copy regenerates it with its full length rather than from
        // the requested start
        assertThat(harness.structure(copy, export).getFirst()).isEqualTo("STAY 2026-10-07T15:42:55Z 40830s");

        // The manual movement type is re-applied to the regenerated trip
        assertThat(firstTripFrom(copy, DAY_START).getMovementType()).isEqualTo(manualType.name());

        // Stays of the day sit at the same latitude, moved east by the shift, and keep their place identity
        List<TimelineStayEntity> sourceStays = staysStartingIn(source, DAY_START, DAY_END);
        List<TimelineStayEntity> copyStays = staysStartingIn(copy, DAY_START, DAY_END);
        assertThat(copyStays).isNotEmpty().hasSameSizeAs(sourceStays);
        for (int i = 0; i < sourceStays.size(); i++) {
            assertThat(copyStays.get(i).getLocation().getY())
                    .isCloseTo(sourceStays.get(i).getLocation().getY(), within(1e-9));
            assertThat(copyStays.get(i).getLocation().getX())
                    .isCloseTo(sourceStays.get(i).getLocation().getX() + LONGITUDE_SHIFT, within(1e-9));
            assertThat(copyStays.get(i).getLocationName()).doesNotContain("Kyiv");
        }
    }

    @Test
    void reportShowsWhereTheRegeneratedTimelineDiffers() throws Exception {
        UUID source = harness.importExport(TimelineReplayHarness.readResource(ISSUE_627_EXPORT), "debug-diff-source");
        byte[] export = export(source, DAY_START, DAY_END, null, null);
        // Pretend the user saw the morning drive end ten minutes later than this server regenerates it
        byte[] tampered = withEntry(export, "timeline.json", json -> {
            for (JsonNode trip : json.get("trips")) {
                if (!Instant.parse(trip.get("timestamp").asText()).isBefore(DAY_START)) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) trip)
                            .put("duration", trip.get("duration").asLong() + 600);
                    return;
                }
            }
        });

        UUID copy = harness.createUser("debug-diff-copy");
        DebugImportReport report = debugImportService.importDebugData(copy, tampered, new DebugImportRequest(true, true));

        assertThat(report.comparison().matches()).isFalse();
        assertThat(report.comparison().different()).isEqualTo(1);
        assertThat(report.comparison().rows()).filteredOn(row -> row.status().equals("DIFFERENT")).singleElement()
                .satisfies(row -> {
                    assertThat(row.expected().type()).isEqualTo("TRIP");
                    assertThat(row.expected().durationSeconds()).isEqualTo(row.actual().durationSeconds() + 600);
                });
        assertThat(report.warnings()).extracting(DebugImportReport.Warning::code).contains("TIMELINE_DIFFERS");
    }

    @Test
    void replayingAnExportInArrivalOrderMatchesTheExportedTimeline() throws Exception {
        UUID source = harness.importExport(TimelineReplayHarness.readResource(ISSUE_627_EXPORT), "debug-arrival-source");
        byte[] export = export(source, DAY_START, DAY_END);
        assertThat(TimelineReplayHarness.arrivalBatches(harness.messages(export))).hasSizeGreaterThan(1);

        UUID live = harness.createUserWithSettingsOf(export, "debug-arrival-live");
        harness.replayByArrival(live, export);

        harness.assertMatchesExportedTimeline(live, export);
        harness.assertMatchesFullRebuild(live, "replayed in arrival order");
    }

    @Test
    void gpsPointsKeepMillisecondsArrivalTimeAndSource() throws Exception {
        UUID source = harness.createUser("debug-rt-points");
        Instant first = Instant.parse("2026-09-01T10:00:00.123Z");
        Instant received = Instant.parse("2026-09-01T10:05:00.456Z");
        installWaterDataset();
        try {
            gpsPointsKeepMillisecondsArrivalTimeSourceAndWater(source, first, received);
        } finally {
            removeWaterDataset();
        }
    }

    private void gpsPointsKeepMillisecondsArrivalTimeSourceAndWater(UUID source, Instant first, Instant received)
            throws Exception {
        QuarkusTransaction.requiringNew().run(() -> {
            GpsPointEntity onWater = persistPoint(source, first, received, GpsSourceType.OVERLAND);
            GpsPointEntity onLand = persistPoint(source, first.plusMillis(500), received, GpsSourceType.OVERLAND);
            persistPoint(source, first.plusSeconds(1), received, GpsSourceType.HOME_ASSISTANT);
            // Outside the requested range and its context: must not be counted
            persistPoint(source, first.plusSeconds(86_400), received, GpsSourceType.OVERLAND);
            entityManager.flush();
            classify(onWater.getId(), true);
            classify(onLand.getId(), false);
        });

        byte[] export = export(source, first.minusSeconds(60), first.plusSeconds(60), null, null);
        JsonNode exportedPoints = objectMapper.readTree(zipEntry(export, "gps_data.json"));
        assertThat(exportedPoints).hasSize(3);
        assertThat(exportedPoints.get(0).get("on_water").asBoolean()).isTrue();
        assertThat(exportedPoints.get(1).get("on_water").asBoolean()).isFalse();
        assertThat(exportedPoints.get(2).has("on_water")).isFalse();
        JsonNode metadata = objectMapper.readTree(zipEntry(export, "metadata.json"));
        assertThat(metadata.get("export_version").asText()).isEqualTo(DebugExportService.EXPORT_VERSION);
        assertThat(metadata.get("gps_point_count").asLong()).isEqualTo(3);

        UUID copy = harness.createUser("debug-rt-points-copy");
        DebugImportReport report = debugImportService.importDebugData(copy, export, new DebugImportRequest(true, true));
        assertThat(report.water().flagsImported()).isEqualTo(2);
        assertThat(report.imported().gpsPoints()).isEqualTo(3);

        List<GpsPointEntity> points = QuarkusTransaction.requiringNew().call(() -> entityManager.createQuery(
                        "select p from GpsPointEntity p where p.user.id = :userId order by p.timestamp",
                        GpsPointEntity.class)
                .setParameter("userId", copy)
                .getResultList());
        assertThat(points).extracting(GpsPointEntity::getTimestamp)
                .containsExactly(first, first.plusMillis(500), first.plusSeconds(1));
        assertThat(points).extracting(GpsPointEntity::getCreatedAt).containsOnly(received);
        assertThat(points).extracting(GpsPointEntity::getSourceType)
                .containsExactly(GpsSourceType.OVERLAND, GpsSourceType.OVERLAND, GpsSourceType.HOME_ASSISTANT);
        assertThat(points).allSatisfy(point -> {
            assertThat(point.getLatitude()).isCloseTo(50.45, within(1e-9));
            assertThat(point.getLongitude()).isCloseTo(30.52 + LONGITUDE_SHIFT, within(1e-9));
            assertThat(point.getVelocity()).isEqualTo(4.0);
        });
        // The copy's points are classified as the user's were, against this server's current water dataset
        String version = QuarkusTransaction.requiringNew().call(
                () -> gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion());
        assertThat(waterFlag(points.get(0).getId(), version)).isEqualTo(Boolean.TRUE);
        assertThat(waterFlag(points.get(1).getId(), version)).isEqualTo(Boolean.FALSE);
        assertThat(waterFlag(points.get(2).getId(), version)).isNull();
    }

    private void installWaterDataset() {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
                        INSERT INTO geo_dataset_metadata (dataset_name, source_url, feature_count, imported_at)
                        VALUES ('water_surface_polygons:debug_export_test', 'test', 1, NOW())
                        ON CONFLICT (dataset_name) DO UPDATE SET feature_count = 1
                        """).executeUpdate());
    }

    private void removeWaterDataset() {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
                "DELETE FROM geo_dataset_metadata WHERE dataset_name = 'water_surface_polygons:debug_export_test'")
                .executeUpdate());
    }

    private void classify(Long pointId, boolean onWater) {
        entityManager.createNativeQuery("""
                        INSERT INTO gps_point_environment (gps_point_id, environment_dataset_version, on_water)
                        VALUES (:id, :version, :onWater)
                        """)
                .setParameter("id", pointId)
                .setParameter("version", gpsPointEnvironmentService.getCurrentEnvironmentDatasetVersion())
                .setParameter("onWater", onWater)
                .executeUpdate();
    }

    private Boolean waterFlag(Long pointId, String version) {
        List<?> flags = QuarkusTransaction.requiringNew().call(() -> entityManager.createNativeQuery("""
                        SELECT on_water FROM gps_point_environment
                        WHERE gps_point_id = :id AND environment_dataset_version = :version
                        """)
                .setParameter("id", pointId)
                .setParameter("version", version)
                .getResultList());
        return flags.isEmpty() ? null : (Boolean) flags.getFirst();
    }

    private void setTimezone(UUID userId, String timezone) {
        QuarkusTransaction.requiringNew().run(() ->
                entityManager.find(UserEntity.class, userId).setTimezone(timezone));
    }

    private String timezone(UUID userId) {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.find(UserEntity.class, userId).getTimezone());
    }

    private byte[] withEntry(byte[] zip, String name, java.util.function.Consumer<JsonNode> change) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip));
             java.util.zip.ZipOutputStream copy = new java.util.zip.ZipOutputStream(output)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                byte[] content = input.readAllBytes();
                if (entry.getName().equals(name)) {
                    JsonNode json = objectMapper.readTree(content);
                    change.accept(json);
                    content = objectMapper.writeValueAsBytes(json);
                }
                copy.putNextEntry(new ZipEntry(entry.getName()));
                copy.write(content);
                copy.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private byte[] export(UUID userId, Instant start, Instant end) throws IOException {
        return export(userId, start, end, null, null);
    }

    private byte[] export(UUID userId, Instant start, Instant end, String issueDescription, Instant issueTime)
            throws IOException {
        DebugExportRequest request = new DebugExportRequest();
        request.setStartTime(start);
        request.setEndTime(end);
        request.setIssueDescription(issueDescription);
        request.setIssueTime(issueTime);
        request.setLatitudeShift(0.0);
        request.setLongitudeShift(LONGITUDE_SHIFT);
        try {
            return QuarkusTransaction.requiringNew().call(() -> debugExportService.generateDebugExport(userId, request));
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }

    private GpsPointEntity persistPoint(UUID userId, Instant timestamp, Instant createdAt, GpsSourceType sourceType) {
        GpsPointEntity point = new GpsPointEntity();
        point.setUser(entityManager.getReference(UserEntity.class, userId));
        point.setDeviceId("phone");
        point.setCoordinates(GeoUtils.createPoint(30.52, 50.45));
        point.setTimestamp(timestamp);
        point.setCreatedAt(createdAt);
        point.setAccuracy(5.0);
        point.setVelocity(4.0);
        point.setSourceType(sourceType);
        entityManager.persist(point);
        return point;
    }

    private TimelineTripEntity firstTripFrom(UUID userId, Instant from) {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.createQuery(
                        "select t from TimelineTripEntity t where t.user.id = :userId and t.timestamp >= :from "
                                + "order by t.timestamp", TimelineTripEntity.class)
                .setParameter("userId", userId)
                .setParameter("from", from)
                .setMaxResults(1)
                .getSingleResult());
    }

    private List<TimelineStayEntity> staysStartingIn(UUID userId, Instant from, Instant to) {
        return QuarkusTransaction.requiringNew().call(() -> entityManager.createQuery(
                        "select s from TimelineStayEntity s where s.user.id = :userId "
                                + "and s.timestamp >= :from and s.timestamp <= :to order by s.timestamp",
                        TimelineStayEntity.class)
                .setParameter("userId", userId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList());
    }

    private static byte[] zipEntry(byte[] zip, String name) throws IOException {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    return input.readAllBytes();
                }
            }
        }
        throw new IllegalArgumentException("Missing " + name);
    }
}
