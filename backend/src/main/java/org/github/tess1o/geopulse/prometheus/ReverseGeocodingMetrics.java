package org.github.tess1o.geopulse.prometheus;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;

import java.util.concurrent.atomic.AtomicLong;

@Singleton
@Slf4j
public class ReverseGeocodingMetrics {

    private final AtomicLong totalReverseGeocodingPoints = new AtomicLong();

    @Inject
    MeterRegistry registry;

    @Inject
    ReverseGeocodingLocationRepository repository;

    @ConfigProperty(name = "geopulse.prometheus.enabled", defaultValue = "true")
    boolean prometheusEnabled;

    @ConfigProperty(name = "geopulse.prometheus.geocoding.enabled", defaultValue = "true")
    boolean geocodingMetricsEnabled;

    void onStart(@Observes StartupEvent ev) {
        if (!prometheusEnabled || !geocodingMetricsEnabled) {
            log.info("Reverse Geocoding metrics disabled");
            return;
        }

        try {
            totalReverseGeocodingPoints.set(repository.count());
            Gauge.builder("reverse_geocoding_total", totalReverseGeocodingPoints, AtomicLong::get)
                    .description("Total number of reverse geocoding cache entries")
                    .register(registry);
        } catch (Exception e) {
            log.error("Failed to initialize Reverse Geocoding metrics", e);
        }
    }

    @Scheduled(every = "${geopulse.prometheus.refresh-interval:10m}")
    void refreshTotals() {
        if (!prometheusEnabled || !geocodingMetricsEnabled) {
            return;
        }
        long count = repository.count();
        totalReverseGeocodingPoints.set(count);
    }
}
