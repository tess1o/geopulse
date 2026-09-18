package org.github.tess1o.geopulse.gps.integrations;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.shared.api.ApiPaths;

import java.util.Map;

@ApplicationScoped
@Slf4j
public class LegacyIngestionRewriter {

    private static final Map<String, String> ROUTES = ApiPaths.LEGACY_INGESTION_ALIASES;

    void register(@Observes Router router) {
        ROUTES.forEach((legacy, target) ->
                // -100 ensures this runs before standard RESTEasy Reactive routing
                router.route(legacy).order(-100).handler(context -> {
                    log.warn("[DEPRECATION] Legacy route {} was called. Rerouting internally to {}", legacy, target);
                    context.reroute(target);
                })
        );
    }
}
