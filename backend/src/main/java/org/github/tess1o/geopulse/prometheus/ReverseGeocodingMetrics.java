package org.github.tess1o.geopulse.prometheus;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.github.tess1o.geopulse.geocoding.repository.ReverseGeocodingLocationRepository;

import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class ReverseGeocodingMetrics {

    private final AtomicLong totalReverseGeocodingPoints = new AtomicLong();

    @Inject
    MeterRegistry registry;

    @Inject
    ReverseGeocodingLocationRepository repository;

    void onStart(@Observes StartupEvent ev) {
        totalReverseGeocodingPoints.set(repository.count());
        Gauge.builder("reverse_geocoding_total", totalReverseGeocodingPoints, AtomicLong::get)
                .register(registry);
    }

    @Scheduled(every = "10m")
    void refreshTotals() {
        long count = repository.count();
        totalReverseGeocodingPoints.set(count);
    }
}
