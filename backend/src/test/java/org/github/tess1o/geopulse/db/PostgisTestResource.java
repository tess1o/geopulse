package org.github.tess1o.geopulse.db;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

public class PostgisTestResource implements QuarkusTestResourceLifecycleManager {

    private static PostgreSQLContainer<?> postgreSQLContainer;

    @Override
    public Map<String, String> start() {
        // In CI environments (GitHub Actions), a PostgreSQL service container is already running
        // Check if we're in CI by looking for the CI environment variable or pre-configured database URL
        String ciEnv = System.getenv("CI");
        String existingDbUrl = System.getenv("QUARKUS_DATASOURCE_JDBC_URL");

        if ("true".equals(ciEnv) || existingDbUrl != null) {
            // Running in CI with existing database - skip Testcontainers
            // Return empty config to use environment variables or application.properties
            return Map.of();
        }

        // Local development - use Testcontainers
        var postgis = DockerImageName.parse("postgis/postgis:17-3.5")
                .asCompatibleSubstituteFor("postgres");
        postgreSQLContainer = new PostgreSQLContainer<>(postgis)
                .withDatabaseName("test")
                .withUsername("postgres")
                .withPassword("password")
                .withReuse(true);

        postgreSQLContainer.start();
        Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.jdbc.url", postgreSQLContainer.getJdbcUrl());
        config.put("quarkus.datasource.username", postgreSQLContainer.getUsername());
        config.put("quarkus.datasource.password", postgreSQLContainer.getPassword());
        return config;
    }

    @Override
    public void stop() {
        if (postgreSQLContainer != null) {
            postgreSQLContainer.stop();
        }
    }
}