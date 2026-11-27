package org.github.tess1o.geopulse.db;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * quarkus.datasource.jdbc.new-connection-sql sets every pooled connection to UTC and turns JIT off: both statements
 * must reach every physical connection and survive transactions on it.
 */
@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
class ConnectionSessionSettingsIntegrationTest {
    @Inject AgroalDataSource dataSource;
    @Inject EntityManager entityManager;

    @Test
    void everyPooledConnectionRunsInUtcWithoutJit() throws SQLException {
        // Held at the same time, so the pool has to open more than one physical connection.
        List<Connection> held = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) {
                held.add(dataSource.getConnection());
                assertUtcWithoutJit(held.getLast());
            }
        } finally {
            for (Connection connection : held) {
                connection.close();
            }
        }
    }

    @Test
    void settingsSurviveCommitAndRollback() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                connection.commit();
                assertUtcWithoutJit(connection);
                statement.execute("SELECT 1");
                connection.rollback();
                assertUtcWithoutJit(connection);
            }
        }
        // Back from the pool after the transactions above.
        try (Connection connection = dataSource.getConnection()) {
            assertUtcWithoutJit(connection);
        }
    }

    @Test
    @Transactional
    void hibernateQueriesRunWithoutJit() {
        Object[] settings = (Object[]) entityManager
                .createNativeQuery("SELECT current_setting('jit'), current_setting('TimeZone')")
                .getSingleResult();
        assertThat(settings).containsExactly("off", "UTC");
    }

    private static void assertUtcWithoutJit(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT current_setting('jit'), current_setting('TimeZone')")) {
            rows.next();
            assertThat(rows.getString(1)).isEqualTo("off");
            assertThat(rows.getString(2)).isEqualTo("UTC");
        }
    }
}
