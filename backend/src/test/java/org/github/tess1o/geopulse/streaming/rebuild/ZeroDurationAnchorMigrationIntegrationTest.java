package org.github.tess1o.geopulse.streaming.rebuild;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.streaming.edits.TimelineEditKind;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
@SerializedDatabaseTest
class ZeroDurationAnchorMigrationIntegrationTest {
    @Inject AgroalDataSource dataSource;

    @Test
    void preservesExistingEditsAndKeepsInvalidIntervalsBlocked() throws Exception {
        String migration;
        try (var resource = getClass().getResourceAsStream("/db/migration/V36.87.0__Allow_zero_duration_trip_edit_anchors.sql")) {
            migration = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            // This transaction-local copy shadows the shared table only until commit.
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TEMP TABLE timeline_edits (
                            kind TEXT NOT NULL,
                            anchor_start TIMESTAMPTZ NOT NULL,
                            anchor_end TIMESTAMPTZ NOT NULL,
                            affected_from TIMESTAMPTZ NOT NULL DEFAULT '2026-10-18T15:00:00Z',
                            affected_to TIMESTAMPTZ NOT NULL DEFAULT '2026-10-18T17:00:00Z',
                            CONSTRAINT chk_timeline_edits_anchor CHECK (anchor_end > anchor_start),
                            CONSTRAINT chk_timeline_edits_affected CHECK (affected_to > affected_from)
                        ) ON COMMIT DROP
                        """);
                for (var kind : TimelineEditKind.values()) {
                    statement.executeUpdate("INSERT INTO timeline_edits (kind, anchor_start, anchor_end) VALUES ('"
                            + kind + "', '2026-10-18T16:01:00Z', '2026-10-18T16:02:00Z')");
                }
                // A failed replacement must leave the original constraint intact.
                rejected(connection, migration.replace("anchor_end > anchor_start OR (", "FALSE AND ("), "23514");
                rejected(connection, pointInsert(TimelineEditKind.SET_MOVEMENT_TYPE), "23514");
                statement.execute(migration);
                try (var rows = statement.executeQuery("SELECT count(*) FROM timeline_edits WHERE anchor_end > anchor_start")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isEqualTo(TimelineEditKind.values().length);
                }
                for (var kind : TimelineEditKind.values()) {
                    if (kind == TimelineEditKind.SET_MOVEMENT_TYPE || kind == TimelineEditKind.SET_TIMES || kind == TimelineEditKind.REMOVE_EVENT) {
                        assertThat(statement.executeUpdate(pointInsert(kind))).isEqualTo(1);
                    } else rejected(connection, pointInsert(kind), "23514");
                    rejected(connection, "INSERT INTO timeline_edits (kind, anchor_start, anchor_end) VALUES ('"
                            + kind + "', '2026-10-18T16:02:00Z', '2026-10-18T16:01:00Z')", "23514");
                }
                rejected(connection, "INSERT INTO timeline_edits (kind, anchor_start, anchor_end) VALUES "
                        + "('SET_MOVEMENT_TYPE', NULL, '2026-10-18T16:01:00Z')", "23502");
                rejected(connection, "INSERT INTO timeline_edits (kind, anchor_start, anchor_end, affected_to) VALUES "
                        + "('SET_MOVEMENT_TYPE', '2026-10-18T16:01:00Z', '2026-10-18T16:01:00Z', '2026-10-18T15:00:00Z')", "23514");
            }
            connection.rollback();
        }
    }

    private static String pointInsert(TimelineEditKind kind) {
        return "INSERT INTO timeline_edits (kind, anchor_start, anchor_end) VALUES ('"
                + kind + "', '2026-10-18T16:01:00Z', '2026-10-18T16:01:00Z')";
    }

    private static void rejected(Connection connection, String sql, String state) throws SQLException {
        var savepoint = connection.setSavepoint();
        try (var statement = connection.createStatement()) {
            var failure = catchThrowable(() -> statement.execute(sql));
            assertThat(failure).isInstanceOf(SQLException.class);
            assertThat(((SQLException) failure).getSQLState()).isEqualTo(state);
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }
    }
}
