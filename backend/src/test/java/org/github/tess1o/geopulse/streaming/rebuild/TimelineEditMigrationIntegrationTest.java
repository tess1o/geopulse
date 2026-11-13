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
import org.github.tess1o.geopulse.streaming.model.dto.TimelineEditRequest;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineEditEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripMovementOverrideEntity;
import org.github.tess1o.geopulse.streaming.repository.*;
import org.github.tess1o.geopulse.streaming.service.*;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.testsupport.*;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Inject ActivityCorrectionService activities;
    @Inject EntityManager em;

    @Test
    void replacesPartOfAMigratedGapStayAndRestoresTheOriginalOnUndo() throws Exception {
        GeocodingTestMocks.install();
        var day = new CommuteWithStops();
        UUID user = day.ingest(harness, "migration-replace-stay");
        var gapEnd = CommuteWithStops.at("16:00");
        harness.ingest(user, new Track().stay(day.office, gapEnd, CommuteWithStops.at("18:00"), 2).messages());
        harness.fullRebuild(user);
        var gap = QuarkusTransaction.requiringNew().call(() -> gaps.findByUserId(user).stream()
                .filter(row -> row.getEndTime().equals(gapEnd)).findFirst().orElseThrow());
        var conversion = new DataGapStayOverrideRequest();
        conversion.setLocationStrategy("SELECTED_LOCATION");
        conversion.setLatitude(day.pharmacy[0]); conversion.setLongitude(day.pharmacy[1]); conversion.setLocationName("Gym");
        gapEdits.convertGapToStay(user, gap.getId(), conversion);
        String migration = scopedUnifyMigration(user);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(migration).executeUpdate());
        harness.fullRebuild(user);

        var original = edits.list(user).getFirst();
        var trip = new ActivityCorrectionService.Activity();
        trip.setSegmentType("TRIP");
        trip.setStartTime(CommuteWithStops.at("14:00"));
        trip.setEndTime(CommuteWithStops.at("15:00"));
        trip.setStartPlace(new ActivityCorrectionService.Place(
                null, null, day.pharmacy[0], day.pharmacy[1], "Gym"));
        trip.setEndPlace(new ActivityCorrectionService.Place(
                null, null, day.bakery[0], day.bakery[1], "Bakery"));
        var replacement = new ActivityCorrectionService.Request(
                null, java.util.List.of(trip), original.anchorStart(), original.anchorEnd(), true);
        assertThat(activities.preview(user, replacement).events()).extracting(event -> event.type())
                .containsExactly("STAY", "TRIP", "STAY");
        trip.setEndTime(original.anchorEnd().plusSeconds(1));
        assertThatThrownBy(() -> activities.save(user, replacement)).hasMessageContaining("inside");
        assertThat(edits.list(user)).containsExactly(original);
        trip.setEndTime(CommuteWithStops.at("15:00"));
        var saved = activities.save(user, replacement);
        assertThat(saved.edits()).hasSize(1);
        for (boolean restore : new boolean[] {false, true}) {
            harness.fullRebuild(user);
            QuarkusTransaction.requiringNew().run(() ->
                    assertThat(stays.find("user.id = ?1 and locationName = ?2", user, "Gym").list()).hasSize(2));
            activities.changeStatus(user, saved.groupId(), false);
            harness.fullRebuild(user);
            QuarkusTransaction.requiringNew().run(() -> {
                var restored = stays.find("user.id = ?1 and locationName = ?2", user, "Gym").singleResult();
                assertThat(restored.getTimestamp()).isEqualTo(original.anchorStart());
                assertThat(restored.getTimestamp().plusSeconds(restored.getStayDuration())).isEqualTo(original.anchorEnd());
            });
            if (!restore) activities.changeStatus(user, saved.groupId(), true);
        }
    }

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

    @Test
    void importedLegacyReplaceStayGroupsBecomeOrdinaryChains() {
        GeocodingTestMocks.install();
        var source = harness.createUser("import-replace-group");
        var forced = new TimelineEditRequest();
        forced.setKind(TimelineEditKind.FORCE_STAY);
        forced.setAnchorStart(CommuteWithStops.at("14:24"));
        forced.setAnchorEnd(CommuteWithStops.at("15:24"));
        forced.setAffectedFrom(CommuteWithStops.at("14:24"));
        forced.setAffectedTo(CommuteWithStops.at("15:24"));
        forced.setLatitude(49.82);
        forced.setLongitude(24.01);
        forced.setPlaceName("Knowle Lane");
        forced.getPayload().put("activityCorrection", true);
        var original = edits.create(source, forced);
        harness.fullRebuild(source);

        var trip = new ActivityCorrectionService.Activity();
        trip.setSegmentType("TRIP");
        trip.setStartTime(CommuteWithStops.at("14:30"));
        trip.setEndTime(CommuteWithStops.at("14:34"));
        trip.setStartPlace(new ActivityCorrectionService.Place(null, null, 49.82, 24.01, "Start"));
        trip.setEndPlace(new ActivityCorrectionService.Place(null, null, 49.83, 24.03, "End"));
        trip.setWaypoints(List.of(new TripReconstructionWaypointDto(49.825, 24.025)));
        var group = activities.save(source, new ActivityCorrectionService.Request(null, List.of(trip),
                original.anchorStart(), original.anchorEnd(), true));
        var groupFirst = group.edits().getFirst().id();
        // Reproduce the 2.3.0 shape: the group's first edit remembers the original seq and the original is superseded.
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("UPDATE timeline_edits SET payload = jsonb_set(payload, "
                            + "'{replacesStaySequence}', to_jsonb(CAST(?2 AS bigint))) WHERE id = ?1")
                    .setParameter(1, groupFirst).setParameter(2, original.seq()).executeUpdate();
            em.createNativeQuery("UPDATE timeline_edits SET status = 'SUPERSEDED', superseded_by = ?2 WHERE id = ?1")
                    .setParameter(1, original.id()).setParameter(2, groupFirst).executeUpdate();
        });

        var archive = manual.exportOf(source, CommuteWithStops.at("00:00"), CommuteWithStops.at("23:59"));
        var target = harness.createUser("import-replace-group-target");
        manual.importInto(target, archive);
        harness.fullRebuild(target);

        var imported = edits.list(target);
        assertThat(imported).filteredOn(edit -> edit.seq() == original.seq())
                .singleElement().satisfies(edit -> assertThat(edit.status()).isEqualTo(TimelineEditStatus.ACTIVE));
        assertThat(imported).allSatisfy(edit -> assertThat(edit.payload()).doesNotContainKey("replacesStaySequence"));
        assertThat(imported).filteredOn(edit -> edit.groupId() != null)
                .singleElement().satisfies(edit -> assertThat(edit.beforeValues()).containsKey("prerequisiteSequences"));
        List<org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity> importedStays =
                QuarkusTransaction.requiringNew().call(() -> stays.find("user.id = ?1 order by timestamp", target).list());
        assertThat(importedStays)
                .extracting(stay -> stay.getTimestamp() + "/" + stay.getStayDuration())
                .containsExactly(CommuteWithStops.at("14:24") + "/360", CommuteWithStops.at("14:34") + "/3000");
        List<TimelineTripEntity> importedTrips = QuarkusTransaction.requiringNew()
                .call(() -> trips.find("user.id = ?1", target).list());
        assertThat(importedTrips)
                .singleElement().satisfies(row -> assertThat(row.getTimestamp()).isEqualTo(CommuteWithStops.at("14:30")));
    }

    @Test
    void replaceStayGroupMigrationIsSafeForEveryDataShape() throws Exception {
        GeocodingTestMocks.install();
        UUID untouched = harness.createUser("mig-untouched");
        UUID affected = harness.createUser("mig-affected");
        UUID malformed = harness.createUser("mig-malformed");
        UUID externallySuperseded = harness.createUser("mig-external");
        UUID undone = harness.createUser("mig-undone");

        UUID affectedGroup = UUID.randomUUID();
        long affectedFirst = seedEdit(affected, 2, TimelineEditKind.FORCE_TRIP, "14:30", "14:34",
                Map.of("replacesStaySequence", 1L), TimelineEditStatus.ACTIVE, affectedGroup, null);
        long affectedOriginal = seedEdit(affected, 1, TimelineEditKind.FORCE_STAY, "14:24", "15:24",
                Map.of("locationName", "Knowle Lane"), TimelineEditStatus.SUPERSEDED, null, affectedFirst);

        UUID malformedGroup = UUID.randomUUID();
        long malformedFirst = seedEdit(malformed, 2, TimelineEditKind.FORCE_TRIP, "14:30", "14:34",
                Map.of("replacesStaySequence", "abc"), TimelineEditStatus.ACTIVE, malformedGroup, null);
        seedEdit(malformed, 3, TimelineEditKind.FORCE_TRIP, "14:31", "14:34",
                Map.of("replacesStaySequence", "999999999999999999999999999"),
                TimelineEditStatus.ACTIVE, malformedGroup, null);
        var nullMarker = new LinkedHashMap<String, Object>();
        nullMarker.put("replacesStaySequence", null);
        seedEdit(malformed, 4, TimelineEditKind.FORCE_TRIP, "14:32", "14:34", nullMarker,
                TimelineEditStatus.ACTIVE, malformedGroup, null);
        seedEdit(malformed, 5, TimelineEditKind.FORCE_TRIP, "14:33", "14:34",
                Map.of("replacesStaySequence", true), TimelineEditStatus.ACTIVE, malformedGroup, null);
        seedEdit(malformed, 6, TimelineEditKind.FORCE_TRIP, "14:34", "14:35",
                Map.of("replacesStaySequence", Map.of("a", 1)), TimelineEditStatus.ACTIVE, malformedGroup, null);
        seedEdit(malformed, 7, TimelineEditKind.FORCE_TRIP, "14:35", "14:36",
                Map.of("replacesStaySequence", List.of(1)), TimelineEditStatus.ACTIVE, malformedGroup, null);
        long malformedOriginal = seedEdit(malformed, 1, TimelineEditKind.FORCE_STAY, "14:24", "15:24",
                Map.of("locationName", "Knowle Lane"), TimelineEditStatus.SUPERSEDED, null, malformedFirst);

        UUID externalGroup = UUID.randomUUID();
        long externalOther = seedEdit(externallySuperseded, 3, TimelineEditKind.FORCE_STAY, "16:00", "16:30",
                Map.of("locationName", "Other"), TimelineEditStatus.ACTIVE, null, null);
        seedEdit(externallySuperseded, 2, TimelineEditKind.FORCE_TRIP, "14:30", "14:34",
                Map.of("replacesStaySequence", 1L), TimelineEditStatus.ACTIVE, externalGroup, null);
        long externalOriginal = seedEdit(externallySuperseded, 1, TimelineEditKind.FORCE_STAY, "14:24", "15:24",
                Map.of("locationName", "Knowle Lane"), TimelineEditStatus.SUPERSEDED, null, externalOther);

        UUID undoneGroup = UUID.randomUUID();
        seedEdit(undone, 2, TimelineEditKind.FORCE_TRIP, "14:30", "14:34",
                Map.of("replacesStaySequence", 1L), TimelineEditStatus.REVERTED, undoneGroup, null);
        long undoneOriginal = seedEdit(undone, 1, TimelineEditKind.FORCE_STAY, "14:24", "15:24",
                Map.of("locationName", "Knowle Lane"), TimelineEditStatus.ACTIVE, null, null);

        runMigrationFile("/db/migration/V36.88.0__Normalize_replace_stay_groups.sql");

        assertThat(findEdit(affectedOriginal).getStatus()).isEqualTo(TimelineEditStatus.ACTIVE);
        assertThat(findEdit(affectedOriginal).getSupersededBy()).isNull();
        assertThat(findEdit(affectedFirst).getPayload()).doesNotContainKey("replacesStaySequence");
        assertThat(findEdit(affectedFirst).getBeforeValues()).containsKey("prerequisiteSequences");
        assertThatThrownBy(() -> edits.revert(affected, affectedOriginal, false))
                .isInstanceOf(org.github.tess1o.geopulse.shared.api.GeoPulseException.class)
                .hasMessageContaining("Undo the later correction first");
        assertThat(harness.pendingRebuild(affected).orElseThrow().rebuildFrom()).isEqualTo(CommuteWithStops.at("14:24"));

        assertThat(findEdit(malformedOriginal).getStatus()).isEqualTo(TimelineEditStatus.SUPERSEDED);
        assertNoMarkers(malformed);
        assertThat(harness.pendingRebuild(malformed)).isEmpty();

        assertThat(findEdit(externalOriginal).getStatus()).isEqualTo(TimelineEditStatus.SUPERSEDED);
        assertNoMarkers(externallySuperseded);
        assertThat(findEdit(externalOther).getBeforeValues()).isNull();
        assertThat(harness.pendingRebuild(externallySuperseded)).isEmpty();

        assertThat(findEdit(undoneOriginal).getStatus()).isEqualTo(TimelineEditStatus.ACTIVE);
        assertNoMarkers(undone);
        assertThat(harness.pendingRebuild(undone)).isEmpty();

        assertThat(edits.list(untouched)).isEmpty();
        assertThat(harness.pendingRebuild(untouched)).isEmpty();

        // A second run (or an imported copy of the same data) must be a no-op rather than a failure.
        runMigrationFile("/db/migration/V36.88.0__Normalize_replace_stay_groups.sql");
        assertThat(findEdit(affectedOriginal).getStatus()).isEqualTo(TimelineEditStatus.ACTIVE);
        assertNoMarkers(malformed);
    }

    private long seedEdit(UUID user, long seq, TimelineEditKind kind, String from, String to,
                          Map<String, Object> payload, TimelineEditStatus status, UUID groupId, Long supersededBy) {
        return QuarkusTransaction.requiringNew().call(() -> {
            var edit = TimelineEditEntity.builder()
                    .user(em.getReference(UserEntity.class, user))
                    .seq(seq)
                    .kind(kind)
                    .anchorStart(CommuteWithStops.at(from))
                    .anchorEnd(CommuteWithStops.at(to))
                    .affectedFrom(CommuteWithStops.at(from))
                    .affectedTo(CommuteWithStops.at(to))
                    .payload(new LinkedHashMap<>(payload))
                    .status(status)
                    .groupId(groupId)
                    .build();
            if (supersededBy != null) {
                edit.setSupersededBy(em.getReference(TimelineEditEntity.class, supersededBy));
            }
            em.persist(edit);
            em.flush();
            return edit.getId();
        });
    }

    private TimelineEditEntity findEdit(long id) {
        return QuarkusTransaction.requiringNew().call(() -> em.find(TimelineEditEntity.class, id));
    }

    private void assertNoMarkers(UUID user) {
        assertThat(edits.list(user)).allSatisfy(edit ->
                assertThat(edit.payload()).doesNotContainKey("replacesStaySequence"));
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
