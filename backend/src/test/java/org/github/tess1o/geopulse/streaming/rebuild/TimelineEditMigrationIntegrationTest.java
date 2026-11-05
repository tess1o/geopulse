package org.github.tess1o.geopulse.streaming.rebuild;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.streaming.edits.*;
import org.github.tess1o.geopulse.streaming.model.dto.DataGapStayOverrideRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripMovementOverrideEntity;
import org.github.tess1o.geopulse.streaming.repository.*;
import org.github.tess1o.geopulse.streaming.service.*;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.testsupport.*;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineEditMigrationIntegrationTest {
    private static final String REPAIR_MIGRATION =
            "/db/migration/V36.80.1__Repair_legacy_timeline_corrections.sql";
    private static final String UNUSABLE_EDITS_MIGRATION =
            "/db/migration/V36.85.0__Drop_unusable_timeline_edits.sql";
    @Inject TimelineReplayHarness harness;
    @Inject ManualEditsSupport manual;
    @Inject TripMovementTypeOverrideService movement;
    @Inject StayNameOverrideService names;
    @Inject DataGapStayOverrideService gapEdits;
    @Inject TimelineDataGapRepository gaps;
    @Inject TimelineStayRepository stays;
    @Inject TimelineTripRepository trips;
    @Inject TimelineEditService edits;
    @Inject EntityManager em;

    @Test
    void seededLegacyCorrectionsMigrateAndReplayWithoutApplyingTwice() throws Exception {
        GeocodingTestMocks.install();
        var day = new CommuteWithStops();
        UUID user = day.ingest(harness, "migration-equivalence");
        harness.ingest(user, new Track().stay(day.office, CommuteWithStops.at("16:00"), CommuteWithStops.at("18:00"), 2).messages());
        harness.fullRebuild(user);
        var source = manual.tripCovering(user, CommuteWithStops.at("08:16"), CommuteWithStops.at("08:20"));
        movement.setManualMovementType(user, source.getId(), TripType.TRAIN);
        manual.split(user, CommuteWithStops.at("08:16"), CommuteWithStops.at("08:20"), day.bakery, "Bakery");
        var gap = QuarkusTransaction.requiringNew().call(() -> gaps.findByUserId(user).stream()
                .filter(row -> row.getEndTime().equals(CommuteWithStops.at("16:00"))).findFirst().orElseThrow());
        var request = new DataGapStayOverrideRequest(); request.setLocationStrategy("SELECTED_LOCATION");
        request.setLatitude(day.pharmacy[0]); request.setLongitude(day.pharmacy[1]); request.setLocationName("Gym");
        gapEdits.convertGapToStay(user, gap.getId(), request);
        names.nameStay(user, CommuteWithStops.at("08:46"), CommuteWithStops.at("12:00"), "Planning day");
        long legacyCount = QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery("""
                select (select count(*) from timeline_trip_stay_split_overrides where user_id = ?1)
                     + (select count(*) from timeline_trip_movement_overrides where user_id = ?1)
                     + (select count(*) from timeline_data_gap_stay_overrides where user_id = ?1)
                     + (select count(*) from timeline_stay_name_overrides where user_id = ?1)
                """).setParameter(1, user).getSingleResult()).longValue());
        String dataMigration = scopedUnifyMigration(user);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(dataMigration).executeUpdate());
        assertThat(edits.list(user)).hasSize((int) legacyCount).extracting(row -> row.kind())
                .contains(TimelineEditKind.FORCE_STAY, TimelineEditKind.SET_MOVEMENT_TYPE, TimelineEditKind.SET_VISIT_NAME);
        harness.fullRebuild(user);
        QuarkusTransaction.requiringNew().run(() -> {
            java.util.List<org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity> migratedStays = stays.find("user.id", user).list();
            assertThat(migratedStays).anySatisfy(row -> {
                assertThat(row.getTimestamp()).isEqualTo(CommuteWithStops.at("08:16"));
                assertThat(row.getStayDuration()).isEqualTo(240); assertThat(row.getLocationName()).isEqualTo("Bakery");
            }).anySatisfy(row -> { assertThat(row.getTimestamp()).isEqualTo(gap.getStartTime());
                assertThat(row.getLocationName()).isEqualTo("Gym");
                assertThat(row.getStayDuration()).isEqualTo(gap.getDurationSeconds());
            }).anySatisfy(row -> { assertThat(row.getTimestamp()).isEqualTo(CommuteWithStops.at("08:46"));
                // Legacy one-visit names become visit labels, independent from the place's name.
                assertThat(row.getVisitName()).isEqualTo("Planning day"); });
            java.util.List<org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity> migratedTrips = trips.find("user.id", user).list();
            assertThat(migratedTrips).hasSize(2).allSatisfy(row -> {
                assertThat(row.getMovementType()).isEqualTo("TRAIN");
                assertThat(row.getMovementTypeSource().name()).isEqualTo("MANUAL");
            });
        });
        var migrated = harness.snapshot(user).content();
        harness.fullRebuild(user);
        assertThat(harness.snapshot(user).content()).containsExactlyElementsOf(migrated);
    }

    @Test
    void zeroLengthMovementCorrectionIsRepairedBeforeTheUnifyMigration() throws Exception {
        UUID user = harness.createUser("issue-657");
        Instant tripStart = Instant.parse("2001-02-03T04:05:06Z");
        long tripDurationSeconds = 600;
        Long tripId = QuarkusTransaction.requiringNew().call(() -> {
            TimelineTripEntity trip = new TimelineTripEntity();
            trip.setUser(em.getReference(UserEntity.class, user));
            trip.setTimestamp(tripStart);
            trip.setTripDuration(tripDurationSeconds);
            trip.setDistanceMeters(1_000);
            trip.setMovementType("WALK");
            trip.setStartPoint(GeoUtils.createPoint(2.0, 41.0));
            trip.setEndPoint(GeoUtils.createPoint(2.01, 41.01));
            em.persist(trip);
            em.flush();
            return trip.getId();
        });
        // A movement-type correction made on a zero-length continuity trip.
        QuarkusTransaction.requiringNew().run(() -> em.persist(TimelineTripMovementOverrideEntity.builder()
                .user(em.getReference(UserEntity.class, user))
                .trip(em.getReference(TimelineTripEntity.class, tripId))
                .movementType("WALK")
                .sourceTripTimestamp(tripStart)
                .sourceTripDurationSeconds(0)
                .sourceDistanceMeters(1_000)
                .sourceStartLatitude(41.0)
                .sourceStartLongitude(2.0)
                .sourceEndLatitude(41.01)
                .sourceEndLongitude(2.01)
                .build()));

        String dataMigration = scopedUnifyMigration(user);

        // A correction with an empty interval cannot become a timeline edit; this is what failed the upgrade.
        Throwable failure = catchThrowable(() -> QuarkusTransaction.requiringNew()
                .run(() -> em.createNativeQuery(dataMigration).executeUpdate()));
        assertThat(failure).isNotNull();
        assertThat(messagesOf(failure)).contains("chk_timeline_edits_affected");

        runMigrationFile(REPAIR_MIGRATION);

        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(dataMigration).executeUpdate());
        assertThat(edits.list(user))
                .filteredOn(edit -> edit.kind() == TimelineEditKind.SET_MOVEMENT_TYPE)
                .singleElement()
                .satisfies(edit -> {
                    assertThat(edit.affectedFrom()).isEqualTo(tripStart);
                    assertThat(edit.affectedTo()).isEqualTo(tripStart.plusSeconds(tripDurationSeconds));
                    assertThat(edit.payload()).containsEntry("movementType", "WALK");
                });
    }

    @Test
    void oversizedSplitCorrectionIsCappedBeforeTheUnifyMigration() throws Exception {
        UUID user = harness.createUser("issue-657-overflow");
        Instant sourceTripStart = Instant.parse("2001-03-04T05:06:07Z");
        Instant stayStart = sourceTripStart.plusSeconds(600);
        Instant stayEnd = stayStart.plusSeconds(600);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                        INSERT INTO timeline_trip_stay_split_overrides (
                            user_id, stay_start_time, stay_end_time, anchor_timestamp,
                            stay_latitude, stay_longitude, stay_location_name,
                            source_trip_timestamp, source_trip_duration_seconds, source_distance_meters,
                            source_start_latitude, source_start_longitude, source_end_latitude, source_end_longitude)
                        VALUES (:user, :stayStart, :stayEnd, :stayStart, 41.0, 2.0, 'Bakery', :sourceStart,
                                9223372036854775807, 1000, 41.0, 2.0, 41.01, 2.01)
                        """)
                .setParameter("user", user)
                .setParameter("stayStart", stayStart)
                .setParameter("stayEnd", stayEnd)
                .setParameter("sourceStart", sourceTripStart)
                .executeUpdate());

        String dataMigration = scopedUnifyMigration(user);

        // Adding the remembered duration to the timestamp overflows before any row reaches the constraints.
        Throwable failure = catchThrowable(() -> QuarkusTransaction.requiringNew()
                .run(() -> em.createNativeQuery(dataMigration).executeUpdate()));
        assertThat(failure).isNotNull();
        assertThat(messagesOf(failure)).contains("interval out of range");

        runMigrationFile(REPAIR_MIGRATION);

        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(dataMigration).executeUpdate());
        assertThat(edits.list(user))
                .filteredOn(edit -> edit.kind() == TimelineEditKind.FORCE_STAY)
                .singleElement()
                .satisfies(edit -> assertThat(edit.affectedTo()).isEqualTo(sourceTripStart.plusSeconds(31_536_000)));
    }

    @Test
    void correctionsTheEditApplierCannotUseAreDroppedBeforeTheUnifyMigration() throws Exception {
        UUID user = harness.createUser("issue-657-unusable");
        Instant at = Instant.parse("2001-04-05T06:07:08Z");
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("""
                            INSERT INTO timeline_trip_movement_overrides (
                                user_id, movement_type, source_trip_timestamp, source_trip_duration_seconds,
                                source_distance_meters, source_start_latitude, source_start_longitude,
                                source_end_latitude, source_end_longitude)
                            VALUES (:user, 'DRIVING', :at, 600, 1000, 41.0, 2.0, 41.01, 2.01)
                            """)
                    .setParameter("user", user)
                    .setParameter("at", at)
                    .executeUpdate();
            em.createNativeQuery("""
                            INSERT INTO timeline_stay_name_overrides (user_id, stay_start_time, stay_end_time, location_name)
                            VALUES (:user, :at, :end, '   ')
                            """)
                    .setParameter("user", user)
                    .setParameter("at", at)
                    .setParameter("end", at.plusSeconds(600))
                    .executeUpdate();
        });

        runMigrationFile(REPAIR_MIGRATION);

        String dataMigration = scopedUnifyMigration(user);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(dataMigration).executeUpdate());
        // The migrated edit applier would fail on the unknown movement type and the blank visit name.
        assertThat(edits.list(user)).isEmpty();
    }

    @Test
    void unusableEditsAlreadyMigratedBy220AreDropped() throws Exception {
        UUID user = harness.createUser("issue-657-migrated");
        Instant at = Instant.parse("2001-05-06T07:08:09Z");
        QuarkusTransaction.requiringNew().run(() -> {
            insertEdit(user, 1, "SET_MOVEMENT_TYPE", "{\"movementType\": \"DRIVING\"}", at);
            insertEdit(user, 2, "SET_VISIT_NAME", "{\"visitName\": \"   \"}", at);
            insertEdit(user, 3, "SET_MOVEMENT_TYPE", "{\"movementType\": \"WALK\"}", at);
        });

        runMigrationFile(UNUSABLE_EDITS_MIGRATION);

        // The applier would throw on the unknown movement type and on the blank visit name.
        assertThat(edits.list(user))
                .singleElement()
                .satisfies(edit -> {
                    assertThat(edit.kind()).isEqualTo(TimelineEditKind.SET_MOVEMENT_TYPE);
                    assertThat(edit.payload()).containsEntry("movementType", "WALK");
                });
    }

    private void insertEdit(UUID user, long seq, String kind, String payload, Instant at) {
        em.createNativeQuery("""
                        INSERT INTO timeline_edits (user_id, seq, kind, anchor_start, anchor_end,
                                                    affected_from, affected_to, payload)
                        VALUES (:user, :seq, :kind, :at, :end, :at, :end, CAST(:payload AS jsonb))
                        """)
                .setParameter("user", user)
                .setParameter("seq", seq)
                .setParameter("kind", kind)
                .setParameter("at", at)
                .setParameter("end", at.plusSeconds(600))
                .setParameter("payload", payload)
                .executeUpdate();
    }

    /** The shipped data migration, scoped to one fixture: Flyway already created its tables. */
    private static String scopedUnifyMigration(UUID user) throws Exception {
        String sql = readMigration("/db/migration/V36.81.0__Unify_timeline_edits.sql");
        String dataMigration = sql.substring(sql.indexOf("WITH legacy AS"));
        for (String table : new String[]{"timeline_trip_stay_split_overrides", "timeline_trip_movement_overrides",
                "timeline_data_gap_stay_overrides", "timeline_stay_name_overrides"}) {
            dataMigration = dataMigration.replace("FROM " + table, "FROM " + table + " WHERE user_id = '" + user + "'")
                    .replace("UPDATE " + table + " SET migrated_to_timeline_edits = TRUE;",
                            "UPDATE " + table + " SET migrated_to_timeline_edits = TRUE WHERE user_id = '" + user + "';");
        }
        return dataMigration;
    }

    private void runMigrationFile(String path) throws Exception {
        for (String statement : readMigration(path).split(";\\s*\\n")) {
            String trimmed = statement.strip();
            if (trimmed.isEmpty() || trimmed.lines().allMatch(line -> line.isBlank() || line.trim().startsWith("--"))) {
                continue;
            }
            QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(statement).executeUpdate());
        }
    }

    private static String readMigration(String path) throws Exception {
        try (var input = TimelineEditMigrationIntegrationTest.class.getResourceAsStream(path)) {
            assertThat(input).as("migration %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String messagesOf(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            messages.append(current).append('\n');
        }
        return messages.toString();
    }
}
