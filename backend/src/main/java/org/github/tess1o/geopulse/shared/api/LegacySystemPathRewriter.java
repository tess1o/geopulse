package org.github.tess1o.geopulse.shared.api;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reroutes the pre-v1 health and metrics paths to their {@code /api/v1/system/*} equivalents.
 *
 * <p>Healthchecks and scrapes hit these paths every few seconds, so the deprecation warning is
 * logged once per legacy path instead of on every request.</p>
 */
@ApplicationScoped
@Slf4j
public class LegacySystemPathRewriter {

    private static final Map<String, String> ROUTES = ApiPaths.LEGACY_SYSTEM_ALIASES;

    private final Set<String> warnedPaths = ConcurrentHashMap.newKeySet();

    void register(@Observes Router router) {
        ROUTES.forEach((legacy, target) ->
                // -100 ensures this runs before standard RESTEasy Reactive and Micrometer routing
                router.route(legacy).order(-100).handler(context -> {
                    if (warnedPaths.add(legacy)) {
                        log.warn("[DEPRECATION] Legacy route {} was called. Rerouting internally to {}. "
                                + "Update healthchecks, probes and scrape configs to the new path.", legacy, target);
                    }
                    context.reroute(target);
                })
        );
    }
}
