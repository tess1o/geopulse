package org.github.tess1o.geopulse.prometheus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class GeoPulseWorkloadMetrics {

    @Inject
    MeterRegistry registry;

    @ConfigProperty(name = "geopulse.prometheus.enabled", defaultValue = "true")
    boolean prometheusEnabled;

    @ConfigProperty(name = "geopulse.prometheus.workload.enabled", defaultValue = "true")
    boolean workloadMetricsEnabled;

    public long start() {
        return System.nanoTime();
    }

    public void recordTimer(String name, long startedAtNanos, String... tags) {
        if (!isEnabled()) {
            return;
        }
        try {
            Timer.builder(name)
                    .tags(tags)
                    .publishPercentileHistogram()
                    .register(registry)
                    .record(System.nanoTime() - startedAtNanos, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            log.debug("Failed to record workload timer {}", name, e);
        }
    }

    public void increment(String name, String... tags) {
        increment(name, 1.0d, tags);
    }

    public void increment(String name, double amount, String... tags) {
        if (!isEnabled()) {
            return;
        }
        try {
            Counter.builder(name)
                    .tags(tags)
                    .register(registry)
                    .increment(amount);
        } catch (Exception e) {
            log.debug("Failed to record workload counter {}", name, e);
        }
    }

    public boolean isEnabled() {
        return prometheusEnabled && workloadMetricsEnabled && registry != null;
    }
}
