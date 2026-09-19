package org.github.tess1o.geopulse.db;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostgisTestResource implements QuarkusTestResourceLifecycleManager {

    public static final String DATABASE_NAME_ARG = "database-name";

    private static final String DEFAULT_DATABASE_NAME = "gp_test_shared";
    private static final Object LOCK = new Object();
    private static PostgreSQLContainer<?> postgreSQLContainer;

    /** Marks that this JVM already registered its database cleanup, shared across classloaders. */
    private static final String CLEANUP_REGISTERED_PROPERTY = "geopulse.test.db-cleanup-registered";

    /**
     * Keeps every JVM on its own databases and temp directories. A shared PostgreSQL server is exactly what CI
     * uses (it passes {@code QUARKUS_DATASOURCE_*}), and the per-process suffix is what stops a run from colliding
     * with a previous run's databases still sitting on that server.
     */
    private static final long PROCESS_ID = ProcessHandle.current().pid();

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
            if (isNonBlank(existingDbUrl) && isNonBlank(existingUsername) && isNonBlank(existingPassword)) {
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
                        .withPassword("password")
                        // Each distinct @TestProfile makes Quarkus discard the classloader that holds
                        // the static container reference above, so without reuse this resource starts a
                        // brand-new container (about 6.5s) roughly a dozen times per suite run. Reuse
                        // lets the next classloader adopt the container still running from the previous
                        // one. Needs testcontainers.reuse.enable=true; without it Testcontainers just
                        // starts a fresh container as before.
                        .withReuse(true);
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
        // Intentionally does not stop the container: the next classloader adopts it (see withReuse above), and
        // reuse keeps it alive past JVM exit so later runs skip startup too. Databases are not dropped here
        // either - Quarkus calls stop() on every profile switch - see registerDatabaseCleanup, which drops this
        // JVM's databases once at exit. The container itself is reaped by `docker rm -f`.
    }

    private static Map<String, String> createSharedDatabaseConfig(String jdbcUrl, String username, String password,
                                                                  String databaseName) {
        String runDatabaseName = databaseName + "_" + PROCESS_ID;
        String adminJdbcUrl = jdbcUrlWithDatabase(jdbcUrl, "postgres");

        // Dropped and recreated on every call rather than created-if-absent: Quarkus runs this resource
        // again for each distinct @TestProfile, and every one of those boots must see an empty schema so
        // that Flyway replays all migrations. Reusing a container (or a server named by env vars) means
        // the database outlives the boot, and tests that assert exact row counts would then run against
        // the previous boot's rows while Flyway reported the schema as already up to date.
        try (Connection connection = DriverManager.getConnection(adminJdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + quoteIdentifier(runDatabaseName) + " WITH (FORCE)");
            statement.execute("CREATE DATABASE " + quoteIdentifier(runDatabaseName));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create test database " + runDatabaseName, e);
        }

        String databaseJdbcUrl = jdbcUrlWithDatabase(jdbcUrl, runDatabaseName);
        try (Connection connection = DriverManager.getConnection(databaseJdbcUrl, username, password)) {
            ensurePostgisSchema(connection);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize shared test database extensions", e);
        }

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.jdbc.url", databaseJdbcUrl);
        config.put("quarkus.datasource.username", username);
        config.put("quarkus.datasource.password", password);
        config.putAll(perProcessDirectoryConfig());
        registerDatabaseCleanup(jdbcUrl, username, password);
        return config;
    }

    /**
     * Drops this JVM's test databases when it exits.
     *
     * <p>The container is reused across runs (see {@code withReuse} in {@link #start()}), so without this every
     * run would leave its own databases behind for as long as the container lives - about 200 MB per full suite
     * run, growing with each one. Cleanup is registered as a shutdown hook rather than done in {@link #stop()}
     * because Quarkus calls {@code stop()} on every profile switch, which would drop the database out from under
     * the tests still using it.</p>
     *
     * <p>Only databases ending in this JVM's process id are matched, so a concurrent run - or another shard of
     * this one - is never affected. Registered once per JVM via a system property, because each profile gets its
     * own classloader and therefore its own statics.</p>
     */
    private static void registerDatabaseCleanup(String jdbcUrl, String username, String password) {
        if (System.getProperty(CLEANUP_REGISTERED_PROPERTY) != null) {
            return;
        }
        System.setProperty(CLEANUP_REGISTERED_PROPERTY, "true");

        String adminJdbcUrl = jdbcUrlWithDatabase(jdbcUrl, "postgres");
        String runSuffixPattern = "%\\_" + PROCESS_ID;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (Connection connection = DriverManager.getConnection(adminJdbcUrl, username, password)) {
                List<String> databases = findDatabasesWithSuffix(connection, runSuffixPattern);
                for (String database : databases) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("DROP DATABASE IF EXISTS " + quoteIdentifier(database) + " WITH (FORCE)");
                    }
                }
            } catch (Exception e) {
                // Best effort: leaving a database behind is harmless, failing during shutdown is not.
            }
        }, "geopulse-test-db-cleanup"));
    }

    private static List<String> findDatabasesWithSuffix(Connection connection, String suffixPattern) throws Exception {
        List<String> databases = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT datname FROM pg_database WHERE datname LIKE ?")) {
            statement.setString(1, suffixPattern);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    databases.add(rs.getString(1));
                }
            }
        }
        return databases;
    }

    /**
     * Gives this JVM its own import/export temp directories. They default to fixed paths under
     * /tmp/geopulse, and the services that own them delete files left behind by a previous run as
     * they start up - so parallel forked JVMs sharing them would delete each other's in-flight files.
     */
    private static Map<String, String> perProcessDirectoryConfig() {
        Path root = Paths.get(System.getProperty("java.io.tmpdir"), "geopulse-test-" + PROCESS_ID);
        return Map.of(
                "geopulse.import.temp-directory", root.resolve("imports").toString(),
                "geopulse.import.chunks-directory", root.resolve("chunks").toString(),
                "geopulse.export.temp-directory", root.resolve("exports").toString()
        );
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
