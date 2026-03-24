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

public class PostgisTestResource implements QuarkusTestResourceLifecycleManager {

    private static final Object LOCK = new Object();
    private static PostgreSQLContainer<?> postgreSQLContainer;
    private static int startedReferences = 0;

    private static String sharedDatabaseName;
    private static String adminJdbcUrl;
    private static String adminUsername;
    private static String adminPassword;
    private static Map<String, String> sharedConfig;

    @Override
    public Map<String, String> start() {
        String existingDbUrl = System.getenv("QUARKUS_DATASOURCE_JDBC_URL");
        String existingUsername = System.getenv("QUARKUS_DATASOURCE_USERNAME");
        String existingPassword = System.getenv("QUARKUS_DATASOURCE_PASSWORD");

        synchronized (LOCK) {
            startedReferences++;

            if (isNonBlank(existingDbUrl) && isNonBlank(existingUsername) && isNonBlank(existingPassword)) {
                if (sharedConfig == null) {
                    sharedConfig = new HashMap<>();
                    sharedConfig.put("quarkus.datasource.jdbc.url", existingDbUrl);
                    sharedConfig.put("quarkus.datasource.username", existingUsername);
                    sharedConfig.put("quarkus.datasource.password", existingPassword);
                }
                return new HashMap<>(sharedConfig);
            }

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

            if (sharedConfig == null) {
                sharedConfig = createSharedDatabaseConfig(
                        postgreSQLContainer.getJdbcUrl(),
                        postgreSQLContainer.getUsername(),
                        postgreSQLContainer.getPassword()
                );
            }

            return new HashMap<>(sharedConfig);
        }
    }

    @Override
    public void stop() {
        synchronized (LOCK) {
            if (startedReferences > 0) {
                startedReferences--;
            }
            // Intentionally keep shared test database for full test JVM lifecycle.
            // Database/container cleanup happens naturally when JVM exits.
        }
    }

    private static Map<String, String> createSharedDatabaseConfig(String jdbcUrl, String username, String password) {
        if (sharedDatabaseName == null) {
            sharedDatabaseName = "gp_test_shared";
            adminJdbcUrl = jdbcUrlWithDatabase(jdbcUrl, "postgres");
            adminUsername = username;
            adminPassword = password;

            try (Connection connection = DriverManager.getConnection(adminJdbcUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + quoteIdentifier(sharedDatabaseName));
            } catch (Exception ignored) {
                // Database may already exist if test resource reinitializes in the same environment.
            }

            String databaseJdbcUrl = jdbcUrlWithDatabase(jdbcUrl, sharedDatabaseName);
            try (Connection connection = DriverManager.getConnection(databaseJdbcUrl, username, password)) {
                ensurePostgisSchema(connection);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize shared test database extensions", e);
            }
        }

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.jdbc.url", jdbcUrlWithDatabase(jdbcUrl, sharedDatabaseName));
        config.put("quarkus.datasource.username", username);
        config.put("quarkus.datasource.password", password);
        return config;
    }

    private static void ensurePostgisSchema(Connection connection) throws Exception {
        String schema = findExtensionSchema(connection, "postgis");
        if (schema == null) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE EXTENSION IF NOT EXISTS postgis");
            }
        }

        if (findExtensionSchema(connection, "postgis_topology") == null) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA IF NOT EXISTS topology");
                statement.execute("CREATE EXTENSION IF NOT EXISTS postgis_topology SCHEMA topology");
            }
        }
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

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
