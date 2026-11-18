package org.github.tess1o.geopulse.streaming.rebuild;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.payload.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Executes the shipped SQL against pre-upgrade rows, including rows written after V36.81. */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineCorrectionMigrationIntegrationTest {
    @Inject AgroalDataSource dataSource;
    @Inject ObjectMapper json;
    @Inject TimelineReplayHarness harness;
    @Inject org.github.tess1o.geopulse.streaming.edits.TimelineEditService edits;
    @Inject org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository stays;
    @Inject org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository trips;

    @Test void repairsLegacyRowsBeforeDroppingThemAndKeepsEveryPayloadReadable() throws Exception {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var sql = connection.createStatement()) {
                sql.execute("CREATE TEMP TABLE timeline_edits (LIKE public.timeline_edits INCLUDING DEFAULTS) ON COMMIT DROP");
                sql.execute("ALTER TABLE timeline_edits ALTER COLUMN group_id DROP NOT NULL");
                sql.execute("ALTER TABLE timeline_edits DROP COLUMN payload_version");
                sql.execute("""
                    CREATE TEMP TABLE timeline_trip_stay_split_overrides (
                      id bigint, user_id uuid, created_at timestamptz default now(), migrated_to_timeline_edits boolean,
                      stay_start_time timestamptz, stay_end_time timestamptz, stay_latitude float8, stay_longitude float8,
                      stay_location_name text, stay_location_name_custom boolean, favorite_id bigint, geocoding_id bigint,
                      source_trip_timestamp timestamptz, source_trip_duration_seconds bigint, source_trip_movement_type text,
                      source_start_latitude float8, source_start_longitude float8, source_end_latitude float8, source_end_longitude float8
                    ) ON COMMIT DROP
                    """);
                sql.execute("""
                    CREATE TEMP TABLE timeline_trip_movement_overrides (
                      id bigint, user_id uuid, created_at timestamptz default now(), migrated_to_timeline_edits boolean,
                      source_trip_timestamp timestamptz, source_trip_duration_seconds bigint, movement_type text,
                      source_start_latitude float8, source_start_longitude float8, source_end_latitude float8, source_end_longitude float8
                    ) ON COMMIT DROP
                    """);
                sql.execute("""
                    CREATE TEMP TABLE timeline_data_gap_stay_overrides (
                      id bigint, user_id uuid, created_at timestamptz default now(), migrated_to_timeline_edits boolean,
                      source_gap_start_time timestamptz, source_gap_end_time timestamptz, location_strategy text,
                      selected_latitude float8, selected_longitude float8, source_before_latitude float8, source_before_longitude float8,
                      selected_location_name text, selected_favorite_id bigint, selected_geocoding_id bigint
                    ) ON COMMIT DROP
                    """);
                sql.execute("""
                    CREATE TEMP TABLE timeline_stay_name_overrides (
                      id bigint, user_id uuid, created_at timestamptz default now(), migrated_to_timeline_edits boolean,
                      stay_start_time timestamptz, stay_end_time timestamptz, location_name text
                    ) ON COMMIT DROP
                    """);
                sql.execute("""
                    CREATE TEMP TABLE timeline_edit_applications (
                      edit_id bigint, target_type varchar(20), target_id bigint, applied_at timestamptz default now(),
                      CONSTRAINT timeline_edit_applications_pkey PRIMARY KEY(edit_id),
                      CONSTRAINT chk_timeline_edit_applications_type CHECK(target_type IN ('STAY','TRIP','DATA_GAP'))
                    ) ON COMMIT DROP
                    """);
                sql.execute("CREATE INDEX idx_timeline_edit_applications_target ON timeline_edit_applications(target_type,target_id)");
                String user = UUID.randomUUID().toString();
                sql.execute("INSERT INTO timeline_trip_stay_split_overrides VALUES (1,'"+user+"',now(),true,'2026-09-15 08:20Z','2026-09-15 08:30Z',49.8,24.1,'Stop',false,null,null,'2026-09-15 08:00Z',3600,'CAR',49.8,24.0,49.8,24.2)");
                sql.execute("INSERT INTO timeline_trip_stay_split_overrides VALUES (2,'"+user+"',now(),false,'2026-09-15 10:20Z','2026-09-15 10:30Z',49.8,24.1,'Later',false,null,null,'2026-09-15 10:00Z',3600,'WALK',49.8,24.0,49.8,24.2)");
                sql.execute("INSERT INTO timeline_trip_movement_overrides VALUES (1,'"+user+"',now(),false,'2026-09-15 16:00Z',3600,'TRAIN',49.8,24.0,49.8,24.2)");
                // An undone split, and a split whose second leg the user retyped after V36.81: the repair adds no leg
                // to the first and only the untouched first leg to the second.
                sql.execute("INSERT INTO timeline_trip_stay_split_overrides VALUES (3,'"+user+"',now(),true,'2026-09-15 18:20Z','2026-09-15 18:30Z',49.8,24.1,'Undone',false,null,null,'2026-09-15 18:00Z',3600,'RUNNING',49.8,24.0,49.8,24.2)");
                sql.execute("INSERT INTO timeline_trip_stay_split_overrides VALUES (4,'"+user+"',now(),true,'2026-09-15 20:20Z','2026-09-15 20:30Z',49.8,24.1,'Kept',false,null,null,'2026-09-15 20:00Z',3600,'RUNNING',49.8,24.0,49.8,24.2)");
                sql.execute("INSERT INTO timeline_data_gap_stay_overrides VALUES (1,'"+user+"',now(),true,'2026-09-15 12:00Z','2026-09-15 13:00Z','LATEST_POINT',null,null,49.8,24.1,null,null,null)");
                sql.execute("INSERT INTO timeline_stay_name_overrides VALUES (1,'"+user+"',now(),false,'2026-09-15 14:00Z','2026-09-15 15:00Z','Lunch')");
                sql.execute("""
                    INSERT INTO timeline_edits(user_id,seq,kind,anchor_start,anchor_end,affected_from,affected_to,payload)
                    VALUES ('%s',1,'FORCE_STAY','2026-09-15 08:20Z','2026-09-15 08:30Z','2026-09-15 08:00Z','2026-09-15 09:00Z','{"latitude":49.8,"longitude":24.1,"locationName":"Stop","movementType":"CAR"}'),
                           ('%s',2,'FORCE_STAY','2026-09-15 12:00Z','2026-09-15 13:00Z','2026-09-15 12:00Z','2026-09-15 13:00Z','{"latitude":49.8,"longitude":24.1}')
                    """.formatted(user,user));
                sql.execute("""
                    INSERT INTO timeline_edits(user_id,seq,kind,anchor_start,anchor_end,affected_from,affected_to,payload,status)
                    VALUES ('%s',3,'FORCE_STAY','2026-09-15 18:20Z','2026-09-15 18:30Z','2026-09-15 18:00Z','2026-09-15 19:00Z','{"latitude":49.8,"longitude":24.1,"locationName":"Undone"}','REVERTED'),
                           ('%s',4,'FORCE_STAY','2026-09-15 20:20Z','2026-09-15 20:30Z','2026-09-15 20:00Z','2026-09-15 21:00Z','{"latitude":49.8,"longitude":24.1,"locationName":"Kept"}','ACTIVE'),
                           ('%s',5,'SET_MOVEMENT_TYPE','2026-09-15 20:31Z','2026-09-15 21:00Z','2026-09-15 20:31Z','2026-09-15 21:00Z','{"movementType":"BICYCLE"}','ACTIVE')
                    """.formatted(user,user,user));
                sql.execute("INSERT INTO timeline_edit_applications(edit_id,target_type,target_id) SELECT id,'STAY',42 FROM timeline_edits WHERE seq=1");
                for (String migration : new String[]{"V36.89.0__Finish_timeline_correction_migration.sql","V36.90.0__Add_set_route_kind.sql","V36.91.0__Timeline_correction_effects.sql","V36.92.0__Drop_legacy_timeline_overrides.sql","V36.93.0__Require_correction_groups.sql","V36.94.0__Type_correction_payloads.sql","V36.95.0__Track_all_correction_effects.sql"}) {
                    try (var resource = getClass().getResourceAsStream("/db/migration/"+migration)) {
                        assertThat(resource).isNotNull(); sql.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
                try (var rows = sql.executeQuery("select seq,kind,payload::text,group_id,payload_version from timeline_edits order by seq")) {
                    int count=0; long previous=0;
                    while(rows.next()) {
                        assertThat(rows.getLong(1)).isGreaterThan(previous); previous=rows.getLong(1);
                        assertThat(rows.getObject(4)).isNotNull();
                        assertThat(rows.getInt(5)).isEqualTo(1);
                        var tree=json.readTree(rows.getString(3));
                        var payload=json.treeToValue(tree, CorrectionPayload.class);
                        assertThat(payload).isInstanceOf(CorrectionPayloads.typeFor(TimelineEditKind.valueOf(rows.getString(2))));
                        count++;
                    }
                    assertThat(count).isEqualTo(13);
                }
                try (var rows=sql.executeQuery("select count(*) from timeline_edits where kind='SET_MOVEMENT_TYPE' and payload->>'movementType' in ('CAR','WALK')")) { rows.next(); assertThat(rows.getInt(1)).isEqualTo(4); }
                try (var rows=sql.executeQuery("select anchor_start from timeline_edits where kind='SET_MOVEMENT_TYPE' and payload->>'movementType'='RUNNING'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getObject(1, java.time.OffsetDateTime.class).toInstant()).isEqualTo(java.time.Instant.parse("2026-09-15T20:00:00Z"));
                    assertThat(rows.next()).as("no leg for the undone split, no leg over the user's later retype").isFalse();
                }
                try (var rows=sql.executeQuery("select payload->>'resolveLocationName' from timeline_edits where seq=2")) { rows.next(); assertThat(rows.getString(1)).isEqualTo("true"); }
                try (var rows=sql.executeQuery("select target_type,target_id from timeline_correction_effects")) { rows.next(); assertThat(rows.getString(1)).isEqualTo("STAY"); assertThat(rows.getLong(2)).isEqualTo(42); }
                for (String table : new String[]{"timeline_trip_stay_split_overrides","timeline_trip_movement_overrides","timeline_data_gap_stay_overrides","timeline_stay_name_overrides"}) {
                    try (var rows=sql.executeQuery("select to_regclass('pg_temp."+table+"')")) { rows.next(); assertThat(rows.getObject(1)).isNull(); }
                }
            } finally { connection.rollback(); }
        }
    }

    /**
     * Legacy rows of a real recorded day, migrated by the shipped SQL and folded by a full rebuild, produce the
     * timeline those overrides described: the split stay, both legs with the split trip's movement type, and the
     * stay name. The legacy re-apply services are gone, so the intended result is the oracle.
     */
    @Test void migratedLegacyOverridesReproduceTheTimelineTheyDescribed() throws Exception {
        org.github.tess1o.geopulse.testsupport.GeocodingTestMocks.install();
        var day = new CommuteWithStops();
        UUID user = day.ingest(harness, "migrated-equivalence");
        org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity trip =
                io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() ->
                        trips.find("user.id = ?1 order by timestamp", user).firstResult());
        var tripStart = trip.getTimestamp();
        var tripEnd = tripStart.plusSeconds(trip.getTripDuration());
        org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity office =
                io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() ->
                        stays.find("user.id = ?1 and timestamp >= ?2 order by timestamp", user, tripEnd).firstResult());
        var officeStart = office.getTimestamp();
        var officeEnd = officeStart.plusSeconds(office.getStayDuration());
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var sql = connection.createStatement()) {
                sql.execute("CREATE TEMP TABLE timeline_edits (LIKE public.timeline_edits INCLUDING DEFAULTS) ON COMMIT DROP");
                sql.execute("ALTER TABLE timeline_edits ALTER COLUMN group_id DROP NOT NULL");
                sql.execute("ALTER TABLE timeline_edits DROP COLUMN payload_version");
                sql.execute("""
                    CREATE TEMP TABLE timeline_trip_stay_split_overrides (
                      id bigint, user_id uuid, created_at timestamptz default now(), migrated_to_timeline_edits boolean,
                      stay_start_time timestamptz, stay_end_time timestamptz, stay_latitude float8, stay_longitude float8,
                      stay_location_name text, stay_location_name_custom boolean, favorite_id bigint, geocoding_id bigint,
                      source_trip_timestamp timestamptz, source_trip_duration_seconds bigint, source_trip_movement_type text,
                      source_start_latitude float8, source_start_longitude float8, source_end_latitude float8, source_end_longitude float8
                    ) ON COMMIT DROP""");
                sql.execute("""
                    CREATE TEMP TABLE timeline_trip_movement_overrides (
                      id bigint, user_id uuid, created_at timestamptz default now(), migrated_to_timeline_edits boolean,
                      source_trip_timestamp timestamptz, source_trip_duration_seconds bigint, movement_type text,
                      source_start_latitude float8, source_start_longitude float8, source_end_latitude float8, source_end_longitude float8
                    ) ON COMMIT DROP""");
                sql.execute("""
                    CREATE TEMP TABLE timeline_data_gap_stay_overrides (
                      id bigint, user_id uuid, created_at timestamptz default now(), migrated_to_timeline_edits boolean,
                      source_gap_start_time timestamptz, source_gap_end_time timestamptz, location_strategy text,
                      selected_latitude float8, selected_longitude float8, source_before_latitude float8, source_before_longitude float8,
                      selected_location_name text, selected_favorite_id bigint, selected_geocoding_id bigint
                    ) ON COMMIT DROP""");
                sql.execute("""
                    CREATE TEMP TABLE timeline_stay_name_overrides (
                      id bigint, user_id uuid, created_at timestamptz default now(), migrated_to_timeline_edits boolean,
                      stay_start_time timestamptz, stay_end_time timestamptz, location_name text
                    ) ON COMMIT DROP""");
                // Legacy rows written after V36.81 (not yet migrated): a split with its trip's movement type, a name.
                sql.execute("INSERT INTO timeline_trip_stay_split_overrides VALUES (1,'%s',now(),false,'%s','%s',%s,%s,'Bakery',false,null,null,'%s',%d,'TRAIN',49.842,24.031,49.835,24.075)"
                        .formatted(user, CommuteWithStops.at("08:16"), CommuteWithStops.at("08:20"), day.bakery[0], day.bakery[1],
                                tripStart, trip.getTripDuration()));
                sql.execute("INSERT INTO timeline_stay_name_overrides VALUES (1,'%s',now(),false,'%s','%s','Desk')"
                        .formatted(user, officeStart, officeEnd));
                for (String migration : new String[]{"V36.89.0__Finish_timeline_correction_migration.sql",
                        "V36.93.0__Require_correction_groups.sql", "V36.94.0__Type_correction_payloads.sql"}) {
                    try (var resource = getClass().getResourceAsStream("/db/migration/" + migration)) {
                        sql.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
                sql.execute("""
                    INSERT INTO public.timeline_edits (user_id, group_id, seq, kind, anchor_start, anchor_end,
                        affected_from, affected_to, payload, payload_version, place_favorite_id, place_geocoding_id,
                        place_latitude, place_longitude, place_name, status, created_at, updated_at)
                    SELECT user_id, group_id, seq, kind, anchor_start, anchor_end, affected_from, affected_to, payload,
                           payload_version, place_favorite_id, place_geocoding_id, place_latitude, place_longitude,
                           place_name, status, created_at, updated_at
                    FROM pg_temp.timeline_edits""");
                connection.commit();
            }
        }

        harness.fullRebuild(user);

        assertThat(edits.list(user)).hasSize(4).allSatisfy(edit -> assertThat(edit.applied()).isTrue());
        var storedTrips = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() ->
                trips.find("user.id = ?1 order by timestamp", user).list());
        assertThat(storedTrips).hasSize(2);
        assertThat(storedTrips.get(0).getTimestamp()).isEqualTo(tripStart);
        assertThat(storedTrips.get(0).getTimestamp().plusSeconds(storedTrips.get(0).getTripDuration()))
                .isEqualTo(CommuteWithStops.at("08:16"));
        assertThat(storedTrips.get(1).getTimestamp()).isEqualTo(CommuteWithStops.at("08:20"));
        assertThat(storedTrips.get(1).getTimestamp().plusSeconds(storedTrips.get(1).getTripDuration())).isEqualTo(tripEnd);
        assertThat(storedTrips).allSatisfy(leg -> {
            assertThat(leg.getMovementType()).isEqualTo("TRAIN");
            assertThat(leg.getMovementTypeSource()).isEqualTo(org.github.tess1o.geopulse.streaming.model.shared.MovementTypeSource.MANUAL);
        });
        var storedStays = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() ->
                stays.find("user.id = ?1 order by timestamp", user).list());
        assertThat(storedStays).anySatisfy(stay -> {
            assertThat(stay.getTimestamp()).isEqualTo(CommuteWithStops.at("08:16"));
            assertThat(stay.getStayDuration()).isEqualTo(240);
            assertThat(stay.getLocationName()).isEqualTo("Bakery");
        });
        assertThat(storedStays).anySatisfy(stay -> {
            assertThat(stay.getTimestamp()).isEqualTo(officeStart);
            assertThat(stay.getVisitName()).isEqualTo("Desk");
        });
    }
}
