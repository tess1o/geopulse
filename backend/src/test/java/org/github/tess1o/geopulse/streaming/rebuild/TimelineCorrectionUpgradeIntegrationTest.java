package org.github.tess1o.geopulse.streaming.rebuild;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.flywaydb.core.Flyway;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.streaming.edits.payload.BeforeValues;
import org.github.tess1o.geopulse.streaming.edits.payload.CorrectionPayload;
import org.github.tess1o.geopulse.streaming.edits.payload.CorrectionPayloads;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Upgrades a real database from the last released schema (V36.88.0) through every timeline correction migration, with
 * the constraints, foreign keys and indexes the production schema has. The seeded rows include every legacy shape the
 * old code or old imports could leave behind, including malformed ones: the upgrade must never fail (a failed migration
 * stops the backend from starting), and afterwards every correction must be readable by the typed model (an unreadable
 * row breaks loading that user's timeline).
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class TimelineCorrectionUpgradeIntegrationTest {

    private static final String RELEASED = "36.88.0";

    @Inject AgroalDataSource dataSource;
    @Inject ObjectMapper json;

    private String database;
    private String url;
    private String user;
    private String password;

    @BeforeEach
    void createDatabase() throws Exception {
        database = "gp_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            connection.setAutoCommit(true);
            sql.execute("CREATE DATABASE " + database);
        }
        String mainUrl = ConfigProvider.getConfig().getValue("quarkus.datasource.jdbc.url", String.class);
        url = mainUrl.replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
        user = ConfigProvider.getConfig().getValue("quarkus.datasource.username", String.class);
        password = ConfigProvider.getConfig().getValue("quarkus.datasource.password", String.class);
    }

    @AfterEach
    void dropDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            connection.setAutoCommit(true);
            sql.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
        }
    }

    @Test
    void upgradingFromTheReleasedSchemaNeverFailsAndLeavesOnlyReadableCorrections() throws Exception {
        flyway(RELEASED).migrate();
        UUID owner;
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            owner = seedReleasedData(connection);
            connection.commit();
        }

        assertThatCode(() -> flyway(null).migrate()).as("the upgrade itself").doesNotThrowAnyException();

        try (Connection connection = connect(); Statement sql = connection.createStatement()) {
            try (ResultSet rows = sql.executeQuery("""
                    select count(*) filter (where not success), count(*) filter (where version = '36.95.0' and success)
                    from flyway_schema_history""")) {
                rows.next();
                assertThat(rows.getInt(1)).as("failed migrations").isZero();
                assertThat(rows.getInt(2)).as("latest correction migration applied").isEqualTo(1);
            }
            for (String table : List.of("timeline_trip_stay_split_overrides", "timeline_trip_movement_overrides",
                    "timeline_data_gap_stay_overrides", "timeline_stay_name_overrides", "timeline_edit_applications")) {
                try (ResultSet rows = sql.executeQuery("select to_regclass('public." + table + "')")) {
                    rows.next();
                    assertThat(rows.getObject(1)).as(table + " is gone").isNull();
                }
            }

            Map<Long, Row> bySeq = new HashMap<>();
            try (ResultSet rows = sql.executeQuery("""
                    select seq, kind, status, payload::text, before_values::text, group_id, payload_version,
                           anchor_start, anchor_end
                    from timeline_edits where user_id = '%s' order by seq""".formatted(owner))) {
                while (rows.next()) {
                    var row = new Row(rows.getLong(1), rows.getString(2), rows.getString(3), rows.getString(4),
                            rows.getString(5), rows.getObject(6), rows.getInt(7),
                            rows.getObject(8, java.time.OffsetDateTime.class).toInstant(),
                            rows.getObject(9, java.time.OffsetDateTime.class).toInstant());
                    bySeq.put(row.seq(), row);
                }
            }

            // Every remaining correction is readable exactly as Hibernate reads it.
            for (Row row : bySeq.values()) {
                JsonNode payload = json.readTree(row.payload());
                assertThatCode(() -> CorrectionPayloads.require(TimelineEditKind.valueOf(row.kind()),
                        json.treeToValue(payload, CorrectionPayload.class)))
                        .as("payload of seq " + row.seq() + " " + row.payload()).doesNotThrowAnyException();
                if (row.beforeValues() != null) {
                    assertThatCode(() -> json.readValue(row.beforeValues(), BeforeValues.class))
                            .as("before values of seq " + row.seq() + " " + row.beforeValues()).doesNotThrowAnyException();
                }
                assertThat(row.groupId()).as("group of seq " + row.seq()).isNotNull();
                assertThat(row.payloadVersion()).isEqualTo(1);
            }

            // Valid released corrections survive unchanged in meaning.
            assertThat(bySeq).containsKeys(1L, 2L, 5L, 7L, 10L, 12L, 14L, 15L, 18L);
            // Corrections the fold could never read are removed, as V36.85.0 did for its cases.
            assertThat(bySeq).doesNotContainKeys(3L, 4L, 6L, 8L, 9L, 11L, 13L, 16L, 17L);
            assertThat(json.readTree(bySeq.get(5L).beforeValues()).fieldNames()).toIterable()
                    .as("only the unreadable before values are dropped").containsExactly("visitName");
            assertThat(bySeq.get(14L).beforeValues()).isNull();
            assertThat(json.readTree(bySeq.get(2L).beforeValues()).get("prerequisiteSequences").get(0).asLong()).isEqualTo(1L);

            // Legacy rows: unusable ones are skipped instead of violating a constraint.
            List<Row> movement = bySeq.values().stream().filter(row -> row.kind().equals("SET_MOVEMENT_TYPE")).toList();
            assertThat(movement).extracting(row -> node(row.payload()).path("movementType").asText())
                    .doesNotContain("SPACESHIP");
            assertThat(movement).noneMatch(row -> row.anchorStart().equals(at("17:00")) || row.anchorStart().equals(at("17:30")));
            assertThat(movement).anyMatch(row -> row.anchorStart().equals(at("16:00"))
                    && node(row.payload()).path("movementType").asText().equals("TRAIN"));
            assertThat(movement).anyMatch(row -> row.anchorStart().equals(at("18:00"))
                    && row.anchorEnd().equals(at("18:00").plusSeconds(31_536_000)));
            assertThat(movement).filteredOn(row -> node(row.payload()).path("movementType").asText().equals("CAR")
                    && row.anchorStart().isBefore(at("09:00"))).hasSize(2);
            assertThat(movement).filteredOn(row -> node(row.payload()).path("movementType").asText().equals("WALK"))
                    .hasSize(2);
            assertThat(bySeq.values()).anyMatch(row -> row.kind().equals("FORCE_STAY") && row.anchorStart().equals(at("19:20")));
            assertThat(bySeq.values()).anyMatch(row -> row.kind().equals("SET_VISIT_NAME")
                    && node(row.payload()).path("visitName").asText().equals("Legacy name"));
            assertThat(bySeq.values()).noneMatch(row -> row.kind().equals("SET_VISIT_NAME")
                    && node(row.payload()).path("visitName").asText().isBlank()
                    && row.anchorStart().equals(at("21:00")));

            Set<Long> seqs = new HashSet<>(bySeq.keySet());
            assertThat(seqs).hasSameSizeAs(bySeq.values());
            try (ResultSet rows = sql.executeQuery("select count(*) from timeline_correction_effects")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
        }
    }

    /**
     * An install still on 2.2.x has only the legacy override tables. It runs V36.80.1 through the latest migration in
     * one go, so every legacy shape the old tables allow must come through, readable.
     */
    @Test
    void upgradingFromLegacyOverridesOnlyNeverFailsAndLeavesOnlyReadableCorrections() throws Exception {
        flyway("36.80.0").migrate();
        UUID owner;
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            owner = insertUser(connection);
            try (Statement sql = connection.createStatement()) {
                for (String row : List.of(
                        legacySplit(owner, "08:20", "08:30", "08:00", 3600, "'CAR'"),
                        legacySplit(owner, "10:20", "10:30", "10:00", 9_000_000_000_000_000L, "'HOVERCRAFT'"),
                        legacySplit(owner, "12:00", "12:30", "12:00", 3600, "null"),
                        movement(owner, "16:00", 3600, "TRAIN"),
                        movement(owner, "17:00", 0, "TRAIN"),
                        movement(owner, "17:30", -60, "TRAIN"),
                        movement(owner, "18:00", 9_000_000_000_000_000L, "BOAT"),
                        movement(owner, "20:00", 3600, "SPACESHIP"),
                        name(owner, "20:30", "21:00", "Legacy name"),
                        name(owner, "21:00", "21:30", "   "))) {
                    sql.execute(row);
                }
                sql.execute("""
                        insert into timeline_data_gap_stay_overrides (user_id, location_strategy, source_gap_start_time,
                            source_gap_end_time, source_gap_duration_seconds, source_before_latitude, source_before_longitude,
                            source_after_latitude, source_after_longitude)
                        values ('%s', 'LATEST_POINT', '%s', '%s', 1800, 49.8, 24.1, 49.8, 24.2)""".formatted(owner, at("22:00"), at("22:30")));
            }
            connection.commit();
        }

        assertThatCode(() -> flyway(null).migrate()).as("the upgrade itself").doesNotThrowAnyException();

        try (Connection connection = connect(); Statement sql = connection.createStatement();
             ResultSet rows = sql.executeQuery("""
                     select kind, payload::text, before_values::text, group_id, anchor_start
                     from timeline_edits where user_id = '%s'""".formatted(owner))) {
            int count = 0;
            Set<String> kinds = new HashSet<>();
            while (rows.next()) {
                count++;
                String kind = rows.getString(1);
                kinds.add(kind);
                JsonNode payload = json.readTree(rows.getString(2));
                assertThatCode(() -> CorrectionPayloads.require(TimelineEditKind.valueOf(kind),
                        json.treeToValue(payload, CorrectionPayload.class)))
                        .as(kind + " " + payload).doesNotThrowAnyException();
                if (rows.getString(3) != null) {
                    String before = rows.getString(3);
                    assertThatCode(() -> json.readValue(before, BeforeValues.class)).doesNotThrowAnyException();
                }
                assertThat(rows.getObject(4)).isNotNull();
            }
            assertThat(count).as("legacy rows that became corrections").isPositive();
            assertThat(kinds).contains("FORCE_STAY", "SET_MOVEMENT_TYPE", "SET_VISIT_NAME");
        }
    }

    private static String legacySplit(UUID owner, String stayStart, String stayEnd, String tripStart, long tripSeconds,
                                      String movementType) {
        return """
                insert into timeline_trip_stay_split_overrides (user_id, stay_start_time, stay_end_time, anchor_timestamp,
                    stay_latitude, stay_longitude, stay_location_name, source_trip_timestamp, source_trip_duration_seconds,
                    source_distance_meters, source_start_latitude, source_start_longitude, source_end_latitude,
                    source_end_longitude, source_trip_movement_type)
                values ('%s', '%s', '%s', '%s', 49.8, 24.1, 'Split stop', '%s', %d, 1000, 49.8, 24.0, 49.8, 24.2, %s)"""
                .formatted(owner, at(stayStart), at(stayEnd), at(stayStart), at(tripStart), tripSeconds, movementType);
    }

    /** The rows the released 2.3.x schema can hold, valid and malformed. */
    private UUID seedReleasedData(Connection connection) throws Exception {
        UUID owner = insertUser(connection);
        try (Statement sql = connection.createStatement()) {
            List<String> edits = new ArrayList<>(List.of(
                    // seq, kind, start, end, payload, before_values, status
                    edit(owner, 1, "FORCE_STAY", "08:20", "08:30",
                            "{\"latitude\":49.8,\"longitude\":24.1,\"locationName\":\"Valid\",\"locationNameCustom\":null,\"movementType\":null}", null, "ACTIVE"),
                    edit(owner, 2, "SET_TIMES", "09:00", "10:00",
                            "{\"start\":\"2026-09-15T09:05:00Z\",\"end\":\"2026-09-15T10:00:00Z\"}",
                            "{\"type\":\"STAY\",\"start\":\"2026-09-15T09:00:00Z\",\"end\":\"2026-09-15T10:00:00Z\",\"prerequisiteSequences\":[1]}", "ACTIVE"),
                    edit(owner, 3, "SET_TIMES", "10:00", "11:00", "{\"start\":\"yesterday\",\"end\":\"2026-09-15T11:00:00Z\"}", null, "ACTIVE"),
                    edit(owner, 4, "REMOVE_EVENT", "11:00", "11:30", "{\"resolution\":\"NUKE\"}", null, "ACTIVE"),
                    edit(owner, 5, "REMOVE_EVENT", "11:30", "12:00", "{\"resolution\":\"GAP\"}",
                            "{\"type\":\"WHATEVER\",\"prerequisiteSequences\":\"x\",\"start\":\"bad\",\"latitude\":{\"a\":1},\"visitName\":\"kept\"}", "ACTIVE"),
                    edit(owner, 6, "SET_MOVEMENT_TYPE", "12:00", "12:30", "{\"movementType\":\"SPACESHIP\"}", null, "ACTIVE"),
                    edit(owner, 7, "SET_VISIT_NAME", "12:30", "13:00", "{\"visitName\":123}", null, "ACTIVE"),
                    edit(owner, 8, "MERGE", "13:00", "13:30", "{\"direction\":\"SIDEWAYS\"}", null, "ACTIVE"),
                    edit(owner, 9, "FORCE_TRIP", "13:30", "14:00",
                            "{\"movementType\":\"WALK\",\"waypoints\":[{\"latitude\":\"abc\",\"longitude\":24.1}],\"activityCorrection\":true}", null, "ACTIVE"),
                    edit(owner, 10, "FORCE_TRIP", "14:00", "14:30",
                            "{\"movementType\":\"WALK\",\"startLatitude\":49.8,\"startLongitude\":24.0,\"endLatitude\":49.8,\"endLongitude\":24.2,"
                                    + "\"waypoints\":[{\"latitude\":49.8,\"longitude\":24.0},{\"latitude\":49.8,\"longitude\":24.2}],\"activityCorrection\":true}", null, "ACTIVE"),
                    edit(owner, 11, "SET_PLACE", "14:30", "15:00", "{\"latitude\":{\"x\":1},\"longitude\":24.1}", null, "ACTIVE"),
                    edit(owner, 12, "SPLIT_STAY", "15:00", "16:00", "{\"splitAt\":\"2026-09-15T15:30:00Z\",\"rightLocationName\":\"Right\"}", null, "ACTIVE"),
                    edit(owner, 13, "SET_VISIT_NAME", "16:00", "16:30", "[]", null, "ACTIVE"),
                    edit(owner, 14, "SET_VISIT_NAME", "16:30", "17:00", "{\"visitName\":\"Kept\"}", "\"oops\"", "ACTIVE"),
                    edit(owner, 15, "FORCE_STAY", "08:20", "08:30", "{\"latitude\":49.8,\"longitude\":24.1,\"locationName\":\"Old\"}", null, "SUPERSEDED"),
                    edit(owner, 16, "SET_VISIT_NAME", "17:00", "17:30", "{\"visitName\":null}", null, "REVERTED"),
                    edit(owner, 17, "SET_MOVEMENT_TYPE", "17:30", "18:00", "{}", null, "ACTIVE"),
                    edit(owner, 18, "SET_TIMES", "18:00", "18:30", "{\"start\":1789466400,\"end\":\"2026-09-15T18:30:00Z\"}", null, "ACTIVE")));
            for (String insert : edits) sql.execute(insert);
            sql.execute("update timeline_edits set group_id = gen_random_uuid() where user_id = '%s' and seq in (1, 2)".formatted(owner));
            sql.execute("""
                    update timeline_edits superseded set superseded_by = replacement.id
                    from timeline_edits replacement
                    where superseded.user_id = '%1$s' and superseded.seq = 15
                      and replacement.user_id = '%1$s' and replacement.seq = 1""".formatted(owner));
            sql.execute("""
                    insert into timeline_edit_applications (edit_id, target_type, target_id)
                    select id, 'STAY', 42 from timeline_edits where user_id = '%s' and seq = 1""".formatted(owner));

            // Legacy splits: one already migrated by V36.81 (its legs need the movement-type repair), one written
            // after it, one with a duration far beyond any trip and an unknown movement type.
            sql.execute(split(owner, true, "08:20", "08:30", "08:00", 3600, "'CAR'"));
            sql.execute(split(owner, false, "10:20", "10:30", "10:00", 3600, "'WALK'"));
            sql.execute(split(owner, false, "19:20", "19:30", "19:00", 9_000_000_000_000_000L, "'HOVERCRAFT'"));
            // Legacy movement types: zero, negative and absurd durations and an unknown type cannot become
            // corrections as they are; only the valid ones may.
            sql.execute(movement(owner, "16:00", 3600, "TRAIN"));
            sql.execute(movement(owner, "17:00", 0, "TRAIN"));
            sql.execute(movement(owner, "17:30", -60, "TRAIN"));
            sql.execute(movement(owner, "20:00", 3600, "SPACESHIP"));
            sql.execute(movement(owner, "18:00", 9_000_000_000_000_000L, "BOAT"));
            // Legacy gap stays and names.
            sql.execute("""
                    insert into timeline_data_gap_stay_overrides (user_id, location_strategy, source_gap_start_time,
                        source_gap_end_time, source_gap_duration_seconds, source_before_latitude, source_before_longitude,
                        source_after_latitude, source_after_longitude)
                    values ('%s', 'LATEST_POINT', '%s', '%s', 1800, 49.8, 24.1, 49.8, 24.2)""".formatted(owner, at("22:00"), at("22:30")));
            sql.execute(name(owner, "20:30", "21:00", "Legacy name"));
            sql.execute(name(owner, "21:00", "21:30", "   "));
        }
        return owner;
    }

    private static String edit(UUID owner, long seq, String kind, String start, String end, String payload,
                               String beforeValues, String status) {
        return """
                insert into timeline_edits (user_id, seq, kind, anchor_start, anchor_end, affected_from, affected_to,
                    payload, before_values, status)
                values ('%s', %d, '%s', '%s', '%s', '%s', '%s', '%s'::jsonb, %s, '%s')"""
                .formatted(owner, seq, kind, at(start), at(end), at(start), at(end), payload.replace("'", "''"),
                        beforeValues == null ? "null" : "'" + beforeValues.replace("'", "''") + "'::jsonb", status);
    }

    private static String split(UUID owner, boolean migrated, String stayStart, String stayEnd, String tripStart,
                                long tripSeconds, String movementType) {
        return """
                insert into timeline_trip_stay_split_overrides (user_id, stay_start_time, stay_end_time, anchor_timestamp,
                    stay_latitude, stay_longitude, stay_location_name, source_trip_timestamp, source_trip_duration_seconds,
                    source_distance_meters, source_start_latitude, source_start_longitude, source_end_latitude,
                    source_end_longitude, source_trip_movement_type, migrated_to_timeline_edits)
                values ('%s', '%s', '%s', '%s', 49.8, 24.1, 'Split stop', '%s', %d, 1000, 49.8, 24.0, 49.8, 24.2, %s, %s)"""
                .formatted(owner, at(stayStart), at(stayEnd), at(stayStart), at(tripStart), tripSeconds, movementType,
                        migrated);
    }

    private static String movement(UUID owner, String start, long seconds, String type) {
        return """
                insert into timeline_trip_movement_overrides (user_id, movement_type, source_trip_timestamp,
                    source_trip_duration_seconds, source_distance_meters, source_start_latitude, source_start_longitude,
                    source_end_latitude, source_end_longitude)
                values ('%s', '%s', '%s', %d, 1000, 49.8, 24.0, 49.8, 24.2)""".formatted(owner, type, at(start), seconds);
    }

    private static String name(UUID owner, String start, String end, String value) {
        return """
                insert into timeline_stay_name_overrides (user_id, stay_start_time, stay_end_time, location_name)
                values ('%s', '%s', '%s', '%s')""".formatted(owner, at(start), at(end), value);
    }

    /** A user row with a value for every required column of the released schema. */
    private static UUID insertUser(Connection connection) throws Exception {
        UUID id = UUID.randomUUID();
        List<String> columns = new ArrayList<>(List.of("id"));
        List<String> values = new ArrayList<>(List.of("'" + id + "'"));
        try (Statement sql = connection.createStatement(); ResultSet rows = sql.executeQuery("""
                select column_name, data_type from information_schema.columns
                where table_schema = 'public' and table_name = 'users' and is_nullable = 'NO'
                  and column_default is null and column_name <> 'id'""")) {
            while (rows.next()) {
                columns.add(rows.getString(1));
                values.add(switch (rows.getString(2)) {
                    case "boolean" -> "false";
                    case "integer", "bigint", "smallint", "numeric", "double precision", "real" -> "0";
                    case "timestamp with time zone", "timestamp without time zone" -> "now()";
                    case "jsonb", "json" -> "'{}'";
                    case "uuid" -> "gen_random_uuid()";
                    default -> rows.getString(1).equals("email") ? "'upgrade-" + id + "@example.com'" : "'x'";
                });
            }
        }
        try (Statement sql = connection.createStatement()) {
            sql.execute("insert into users (" + String.join(", ", columns) + ") values (" + String.join(", ", values) + ")");
        }
        return id;
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .outOfOrder(true)
                .baselineOnMigrate(true)
                .initSql("SET TIME ZONE 'UTC'");
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private JsonNode node(String text) {
        try {
            return json.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Connection connect() throws Exception {
        Connection connection = DriverManager.getConnection(url, user, password);
        try (Statement sql = connection.createStatement()) {
            sql.execute("SET TIME ZONE 'UTC'");
        }
        return connection;
    }

    private static java.time.Instant at(String time) {
        return CommuteWithStops.at(time);
    }

    private record Row(long seq, String kind, String status, String payload, String beforeValues, Object groupId,
                       int payloadVersion, java.time.Instant anchorStart, java.time.Instant anchorEnd) {
    }
}
