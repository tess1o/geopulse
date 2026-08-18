package org.github.tess1o.geopulse.prometheus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
@Slf4j
public class GeoPulseWorkloadMetrics {

    private final ConcurrentMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();

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

    public void setGauge(String name, long value, String... tags) {
        if (!isEnabled()) {
            return;
        }
        try {
            String key = name + '\0' + Arrays.toString(tags);
            AtomicLong holder = gauges.computeIfAbsent(key, ignored -> {
                AtomicLong created = new AtomicLong();
                Gauge.builder(name, created, AtomicLong::get)
                        .tags(tags)
                        .register(registry);
                return created;
            });
            holder.set(value);
        } catch (Exception e) {
            log.debug("Failed to update workload gauge {}", name, e);
        }
    }

    public boolean isEnabled() {
        return prometheusEnabled && workloadMetricsEnabled && registry != null;
    }
}
