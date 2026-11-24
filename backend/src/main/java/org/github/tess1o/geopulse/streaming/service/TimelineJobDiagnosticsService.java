package org.github.tess1o.geopulse.streaming.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.Config;
import org.github.tess1o.geopulse.geocoding.config.GeocodingConfigurationService;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.TimelineJobProgress;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the "Copy diagnostics" JSON of a timeline job: the job's steps, timings, counters and memory, plus the
 * application, settings and database facts that change how much work a rebuild is and how long it takes.
 * <p>
 * It never contains the user's id, email, coordinates, place or favorite names, notes, labels or event times: the
 * rebuilt period is given as dates only. Provider URLs and API keys are left out too.
 */
@ApplicationScoped
@Slf4j
public class TimelineJobDiagnosticsService {

    /** Environment variables that carry the backend's JVM / native-image memory options. */
    private static final List<String> MEMORY_OPTION_VARIABLES = List.of("GEOPULSE_BACKEND_NATIVE_IMAGE_OPTS_DEFAULT",
            "NATIVE_IMAGE_OPTS", "NATIVE_IMAGE_OPTS_EXTRA", "JAVA_OPTS", "JAVA_OPTS_EXTRA", "JAVA_OPTS_APPEND");

    @Inject
    Config config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TimelineConfigurationProvider timelineConfiguration;

    @Inject
    GeocodingConfigurationService geocodingConfiguration;

    @Inject
    DataSource dataSource;

    public Map<String, Object> diagnostics(TimelineJobProgress job) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> jobPart = new LinkedHashMap<>();
        jobPart.put("id", job.getJobId());
        jobPart.put("type", job.getType());
        jobPart.put("status", job.getStatus());
        jobPart.put("startTime", job.getStartTime());
        jobPart.put("endTime", job.getEndTime());
        jobPart.put("durationMs", job.getDurationMs());
        jobPart.put("errorMessage", job.getErrorMessage());
        result.put("job", jobPart);
        result.put("summary", job.getSummary());
        result.put("steps", job.getSteps());
        result.put("resources", job.getResources());
        result.put("application", application());
        result.put("settings", settings(job));
        result.put("database", database());
        return result;
    }

    private Map<String, Object> application() {
        Map<String, Object> application = new LinkedHashMap<>();
        application.put("version", config.getOptionalValue("quarkus.application.version", String.class).orElse("unknown"));
        application.put("build", System.getProperty("org.graalvm.nativeimage.imagecode") != null ? "native" : "jvm");
        application.put("cpuCount", Runtime.getRuntime().availableProcessors());
        Map<String, String> memoryOptions = new LinkedHashMap<>();
        for (String variable : MEMORY_OPTION_VARIABLES) {
            String value = System.getenv(variable);
            if (value != null && !value.isBlank()) {
                memoryOptions.put(variable, memoryFlags(value));
            }
        }
        application.put("memoryOptions", memoryOptions);
        return application;
    }

    /** Only the -X options of a JVM / native-image option string: system properties (-D) may hold secrets. */
    static String memoryFlags(String options) {
        return String.join(" ", Arrays.stream(options.trim().split("\\s+"))
                .filter(option -> option.startsWith("-X"))
                .toList());
    }

    private Map<String, Object> settings(TimelineJobProgress job) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("timelineProcessingThreads",
                config.getOptionalValue("geopulse.timeline.processing.thread-pool-size", Integer.class).orElse(null));
        Map<String, Object> geocoding = new LinkedHashMap<>();
        geocoding.put("primaryProvider", geocodingConfiguration.getPrimaryProvider());
        geocoding.put("fallbackProvider", geocodingConfiguration.getFallbackProvider());
        geocoding.put("delayBetweenExternalCallsMs", geocodingConfiguration.getPrimaryProviderDelayMs());
        settings.put("geocoding", geocoding);
        try {
            settings.put("timeline", objectMapper.convertValue(
                    timelineConfiguration.getConfigurationForUser(job.getUserId()),
                    new TypeReference<Map<String, Object>>() {
                    }));
        } catch (RuntimeException e) {
            log.debug("Timeline settings unavailable for job diagnostics", e);
        }
        return settings;
    }

    private Map<String, Object> database() {
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("poolMaxSize", config.getOptionalValue("quarkus.datasource.jdbc.max-size", Integer.class).orElse(null));
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT current_setting('server_version'), postgis_lib_version(), "
                     + "current_setting('shared_buffers'), current_setting('work_mem'), "
                     + "current_setting('max_connections')")) {
            if (row.next()) {
                database.put("postgresVersion", row.getString(1));
                database.put("postgisVersion", row.getString(2));
                database.put("sharedBuffers", row.getString(3));
                database.put("workMem", row.getString(4));
                database.put("maxConnections", row.getString(5));
            }
        } catch (Exception e) {
            log.debug("Database settings unavailable for job diagnostics", e);
        }
        return database;
    }
}
