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
        var postgis = DockerImageName.parse("postgis/postgis:15-3.3")
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