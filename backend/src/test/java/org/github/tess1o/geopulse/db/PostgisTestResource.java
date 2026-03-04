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
import java.util.regex.Matcher;

public class PostgisTestResource implements QuarkusTestResourceLifecycleManager {

    private static final Object LOCK = new Object();
    private static PostgreSQLContainer<?> postgreSQLContainer;
    private static int startedReferences = 0;

    private String schemaName;
    private String adminJdbcUrl;
    private String adminUsername;
    private String adminPassword;

    @Override
    public Map<String, String> start() {
        String ciEnv = System.getenv("CI");
        String existingDbUrl = System.getenv("QUARKUS_DATASOURCE_JDBC_URL");

        if ("true".equals(ciEnv) || existingDbUrl != null) {
            String username = System.getenv("QUARKUS_DATASOURCE_USERNAME");
            String password = System.getenv("QUARKUS_DATASOURCE_PASSWORD");
            if (existingDbUrl == null || username == null || password == null) {
                // Running in CI with externally provided DB settings.
                return Map.of();
            }
            return createSchemaConfig(existingDbUrl, username, password);
        }

        synchronized (LOCK) {
            if (postgreSQLContainer == null) {
                var postgis = DockerImageName.parse("postgis/postgis:17-3.5")
                        .asCompatibleSubstituteFor("postgres");
                postgreSQLContainer = new PostgreSQLContainer<>(postgis)
                        .withDatabaseName("test")
                        .withUsername("postgres")
                        .withPassword("password")
                        .withReuse(true);
                postgreSQLContainer.start();
            }
            startedReferences++;
        }

        return createSchemaConfig(
                postgreSQLContainer.getJdbcUrl(),
                postgreSQLContainer.getUsername(),
                postgreSQLContainer.getPassword()
        );
    }

    @Override
    public void stop() {
        synchronized (LOCK) {
            dropSchemaIfNeeded();
            if (startedReferences > 0) {
                startedReferences--;
            }
            // Keep reusable container alive for the full run to avoid startup cost per class.
        }
    }

    private Map<String, String> createSchemaConfig(String jdbcUrl, String username, String password) {
        schemaName = "gp_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        adminJdbcUrl = jdbcUrl;
        adminUsername = username;
        adminPassword = password;

        String postgisSchema;
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(schemaName));
            postgisSchema = ensurePostgisSchema(connection);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare test schema: " + schemaName, e);
        }

        String jdbcWithSchema = withSchema(jdbcUrl, schemaName, postgisSchema);

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.jdbc.url", jdbcWithSchema);
        config.put("quarkus.datasource.username", username);
        config.put("quarkus.datasource.password", password);
        config.put("quarkus.flyway.schemas", schemaName);
        config.put("quarkus.flyway.default-schema", schemaName);
        config.put("quarkus.hibernate-orm.database.default-schema", schemaName);
        return config;
    }

    private static String ensurePostgisSchema(Connection connection) throws Exception {
        String schema = findExtensionSchema(connection, "postgis");
        if (schema == null) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE EXTENSION IF NOT EXISTS postgis SCHEMA public");
                statement.execute("CREATE EXTENSION IF NOT EXISTS postgis_topology SCHEMA public");
            }
            return "public";
        }

        // Keep topology present in the same extension schema when possible.
        if (findExtensionSchema(connection, "postgis_topology") == null) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE EXTENSION IF NOT EXISTS postgis_topology SCHEMA " + quoteIdentifier(schema));
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

    private void dropSchemaIfNeeded() {
        if (schemaName == null || adminJdbcUrl == null || adminUsername == null || adminPassword == null) {
            return;
        }

        try (Connection connection = DriverManager.getConnection(adminJdbcUrl, adminUsername, adminPassword);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + quoteIdentifier(schemaName) + " CASCADE");
        } catch (Exception ignored) {
            // Ignore cleanup failures to avoid masking test outcomes.
        } finally {
            schemaName = null;
            adminJdbcUrl = null;
            adminUsername = null;
            adminPassword = null;
        }
    }

    private static String withSchema(String jdbcUrl, String schema, String postgisSchema) {
        StringBuilder searchPath = new StringBuilder(schema);
        if (postgisSchema != null
                && !postgisSchema.isBlank()
                && !postgisSchema.equals(schema)
                && !postgisSchema.equals("public")) {
            searchPath.append(',').append(postgisSchema);
        }
        if (!"public".equals(schema)) {
            searchPath.append(",public");
        }

        String value = searchPath.toString();
        if (jdbcUrl.contains("currentSchema=")) {
            return jdbcUrl.replaceFirst(
                    "currentSchema=[^&]*",
                    "currentSchema=" + Matcher.quoteReplacement(value)
            );
        }

        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + separator + "currentSchema=" + value;
    }

    private static String quoteIdentifier(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
