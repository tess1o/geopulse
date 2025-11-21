package org.github.tess1o.geopulse.prometheus;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.*;

@ApplicationScoped
@Slf4j
public class NativeProcessMemoryMetrics {

    private final MeterRegistry registry;

    @ConfigProperty(name = "geopulse.prometheus.enabled", defaultValue = "true")
    boolean prometheusEnabled;

    @ConfigProperty(name = "geopulse.prometheus.memory.enabled", defaultValue = "true")
    boolean memoryMetricsEnabled;

    @Inject
    public NativeProcessMemoryMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void onStart(@Observes StartupEvent ev) {
        if (!prometheusEnabled || !memoryMetricsEnabled) {
            log.info("Native process memory metrics disabled");
            return;
        }

        try {
            log.info("Registering native process memory metrics");
            registry.gauge("process_resident_memory_bytes", this, p -> p.getRss());
            registry.gauge("process_virtual_memory_bytes", this, p -> p.getVsize());
        } catch (Exception e) {
            log.error("Failed to initialize native process memory metrics", e);
        }
    }

    private long getRss() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/self/status"))) {
            return br.lines()
                    .filter(l -> l.startsWith("VmRSS:"))
                    .map(l -> l.replaceAll("\\D+", ""))
                    .mapToLong(Long::parseLong)
                    .findFirst()
                    .orElse(0L) * 1024;
        } catch (Exception e) {
            return 0;
        }
    }

    private long getVsize() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/self/status"))) {
            return br.lines()
                    .filter(l -> l.startsWith("VmSize:"))
                    .map(l -> l.replaceAll("\\D+", ""))
                    .mapToLong(Long::parseLong)
                    .findFirst()
                    .orElse(0L) * 1024;
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Check if metrics are enabled
     * @return true if metrics are enabled
     */
    public boolean isEnabled() {
        return prometheusEnabled && memoryMetricsEnabled;
    }

    /**
     * Get resident memory size in bytes (public API for admin dashboard)
     * Note: This always queries /proc/self/status directly regardless of metrics being enabled
     * @return RSS memory in bytes
     */
    public long getResidentMemoryBytes() {
        return getRss();
    }

    /**
     * Get virtual memory size in bytes (public API)
     * @return virtual memory in bytes
     */
    public long getVirtualMemoryBytes() {
        return getVsize();
    }
}