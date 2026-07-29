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

    public static final String DATABASE_NAME_ARG = "database-name";

    private static final String DEFAULT_DATABASE_NAME = "gp_test_shared";
    private static final Object LOCK = new Object();
    private static PostgreSQLContainer<?> postgreSQLContainer;
    private static int startedReferences = 0;

    private static final Map<String, Map<String, String>> sharedConfigs = new HashMap<>();

    private String databaseName = DEFAULT_DATABASE_NAME;

    @Override
    public void init(Map<String, String> initArgs) {
        String configuredDatabaseName = initArgs.get(DATABASE_NAME_ARG);
        if (isNonBlank(configuredDatabaseName)) {
            databaseName = configuredDatabaseName;
        }
    }

    @Override
    public Map<String, String> start() {
        String existingDbUrl = System.getenv("QUARKUS_DATASOURCE_JDBC_URL");
        String existingUsername = System.getenv("QUARKUS_DATASOURCE_USERNAME");
        String existingPassword = System.getenv("QUARKUS_DATASOURCE_PASSWORD");

        synchronized (LOCK) {
            startedReferences++;

            if (isNonBlank(existingDbUrl) && isNonBlank(existingUsername) && isNonBlank(existingPassword)) {
                if (DEFAULT_DATABASE_NAME.equals(databaseName)) {
                    return Map.of(
                            "quarkus.datasource.jdbc.url", existingDbUrl,
                            "quarkus.datasource.username", existingUsername,
                            "quarkus.datasource.password", existingPassword
                    );
                }

                return new HashMap<>(sharedConfigs.computeIfAbsent(
                        configKey(existingDbUrl, existingUsername, databaseName),
                        ignored -> createSharedDatabaseConfig(existingDbUrl, existingUsername, existingPassword, databaseName)
                ));
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

            return new HashMap<>(sharedConfigs.computeIfAbsent(
                    configKey(postgreSQLContainer.getJdbcUrl(), postgreSQLContainer.getUsername(), databaseName),
                    ignored -> createSharedDatabaseConfig(
                            postgreSQLContainer.getJdbcUrl(),
                            postgreSQLContainer.getUsername(),
                            postgreSQLContainer.getPassword(),
                            databaseName
                    )
            ));
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

    private static Map<String, String> createSharedDatabaseConfig(String jdbcUrl, String username, String password,
                                                                  String databaseName) {
        String adminJdbcUrl = jdbcUrlWithDatabase(jdbcUrl, "postgres");

        try (Connection connection = DriverManager.getConnection(adminJdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + quoteIdentifier(databaseName));
        } catch (Exception ignored) {
            // Database may already exist if test resource reinitializes in the same environment.
        }

        String databaseJdbcUrl = jdbcUrlWithDatabase(jdbcUrl, databaseName);
        try (Connection connection = DriverManager.getConnection(databaseJdbcUrl, username, password)) {
            ensurePostgisSchema(connection);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize shared test database extensions", e);
        }

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.jdbc.url", databaseJdbcUrl);
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

    private static String configKey(String jdbcUrl, String username, String databaseName) {
        return jdbcUrl + "|" + username + "|" + databaseName;
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
