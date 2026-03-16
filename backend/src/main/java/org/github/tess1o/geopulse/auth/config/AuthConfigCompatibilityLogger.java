package org.github.tess1o.geopulse.auth.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class AuthConfigCompatibilityLogger {

    void onStart(@Observes StartupEvent ignored) {
        String corsOrigins = env("GEOPULSE_CORS_ORIGINS");
        String legacyUiUrl = env("GEOPULSE_UI_URL");
        String corsEnabled = env("GEOPULSE_CORS_ENABLED");

        if (isBlank(corsOrigins) && !isBlank(legacyUiUrl)) {
            log.warn("DEPRECATION WARNING: GEOPULSE_UI_URL is used as legacy CORS fallback.");
            log.warn("Set GEOPULSE_CORS_ORIGINS for explicit CORS origins. GEOPULSE_UI_URL support will be removed in a future release.");
        }

        if (!isBlank(corsEnabled) && Boolean.FALSE.toString().equalsIgnoreCase(corsEnabled) && !isBlank(corsOrigins)) {
            log.info("CORS is disabled (GEOPULSE_CORS_ENABLED=false); GEOPULSE_CORS_ORIGINS is currently ignored.");
        }
    }

    private static String env(String name) {
        return System.getenv(name);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
