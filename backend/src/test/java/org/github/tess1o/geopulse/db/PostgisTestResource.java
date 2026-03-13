package org.github.tess1o.geopulse.db;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PostgisTestResource implements QuarkusTestResourceLifecycleManager {

    private static final Object LOCK = new Object();
    private static PostgreSQLContainer<?> postgreSQLContainer;
    private static int startedReferences = 0;

    private String databaseName;
    private String adminJdbcUrl;
    private String databaseJdbcUrl;
    private String adminUsername;
    private String adminPassword;

    @Override
    public Map<String, String> start() {
        String existingDbUrl = System.getenv("QUARKUS_DATASOURCE_JDBC_URL");
        String existingUsername = System.getenv("QUARKUS_DATASOURCE_USERNAME");
        String existingPassword = System.getenv("QUARKUS_DATASOURCE_PASSWORD");

        if (isNonBlank(existingDbUrl) && isNonBlank(existingUsername) && isNonBlank(existingPassword)) {
            return createDatabaseConfig(existingDbUrl, existingUsername, existingPassword);
        }

        synchronized (LOCK) {
            if (postgreSQLContainer == null) {
                String postgisImage = System.getenv("GEOPULSE_TEST_POSTGIS_IMAGE");
                if (postgisImage == null || postgisImage.isBlank()) {
                    // Keep test DB behavior aligned with CI and production.
                    postgisImage = "postgis/postgis:17-3.5";
                }
                var postgis = DockerImageName.parse(postgisImage)
                        .asCompatibleSubstituteFor("postgres");
                postgreSQLContainer = new PostgreSQLContainer<>(postgis)
                        .withDatabaseName("test")
                        .withUsername("postgres")
                        .withPassword("password");
                postgreSQLContainer.start();
            }
            startedReferences++;
        }

        return createDatabaseConfig(
                postgreSQLContainer.getJdbcUrl(),
                postgreSQLContainer.getUsername(),
                postgreSQLContainer.getPassword()
        );
    }

    @Override
    public void stop() {
        synchronized (LOCK) {
            dropDatabaseIfNeeded();
            if (startedReferences > 0) {
                startedReferences--;
            }
            // Keep reusable container alive for the full run to avoid startup cost per class.
        }
    }

    private Map<String, String> createDatabaseConfig(String jdbcUrl, String username, String password) {
        databaseName = "gp_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        adminJdbcUrl = jdbcUrlWithDatabase(jdbcUrl, "postgres");
        databaseJdbcUrl = jdbcUrlWithDatabase(jdbcUrl, databaseName);
        adminUsername = username;
        adminPassword = password;

        try (Connection connection = DriverManager.getConnection(adminJdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + quoteIdentifier(databaseName));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare test database: " + databaseName, e);
        }

        try (Connection connection = DriverManager.getConnection(databaseJdbcUrl, username, password)) {
            ensurePostgisSchema(connection);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize test database extensions: " + databaseName, e);
        }

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.jdbc.url", databaseJdbcUrl);
        config.put("quarkus.datasource.username", username);
        config.put("quarkus.datasource.password", password);
        return config;
    }

    private static String ensurePostgisSchema(Connection connection) throws Exception {
        String schema = findExtensionSchema(connection, "postgis");
        if (schema == null) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE EXTENSION IF NOT EXISTS postgis");
            }
            schema = "public";
        }

        if (findExtensionSchema(connection, "postgis_topology") == null) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA IF NOT EXISTS topology");
                statement.execute("CREATE EXTENSION IF NOT EXISTS postgis_topology SCHEMA topology");
            }
        }

        return schema;
    }

    private static String findExtensionSchema(Connection connection, String extensionName) throws Exception {
        String sql = """
                SELECT n.nspname
                FROM pg_extension e
                JOIN pg_namespace n ON n.oid = e.extnamespace
                WHERE e.extname = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, extensionName);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
                return null;
            }
        }
    }

    private void dropDatabaseIfNeeded() {
        if (databaseName == null || adminJdbcUrl == null || adminUsername == null || adminPassword == null) {
            return;
        }

        try (Connection connection = DriverManager.getConnection(adminJdbcUrl, adminUsername, adminPassword);
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = "
                    + quoteLiteral(databaseName) + " AND pid <> pg_backend_pid()");
            statement.execute("DROP DATABASE IF EXISTS " + quoteIdentifier(databaseName));
        } catch (Exception ignored) {
            // Ignore cleanup failures to avoid masking test outcomes.
        } finally {
            databaseName = null;
            adminJdbcUrl = null;
            databaseJdbcUrl = null;
            adminUsername = null;
            adminPassword = null;
        }
    }

    private static String jdbcUrlWithDatabase(String jdbcUrl, String database) {
        String sanitized = jdbcUrl.replaceFirst("([?&])currentSchema=[^&]*", "$1")
                .replace("?&", "?")
                .replaceAll("[?&]$", "");

        int schemeSeparator = sanitized.indexOf("://");
        int pathStart = sanitized.indexOf('/', schemeSeparator + 3);
        if (pathStart < 0) {
            return sanitized + "/" + database;
        }
        int queryStart = sanitized.indexOf('?', pathStart);
        String prefix = sanitized.substring(0, pathStart + 1);
        String suffix = queryStart >= 0 ? sanitized.substring(queryStart) : "";
        return prefix + database + suffix;
    }

    private static String quoteIdentifier(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private static String quoteLiteral(String value) {
        return '\'' + value.replace("'", "''") + '\'';
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
