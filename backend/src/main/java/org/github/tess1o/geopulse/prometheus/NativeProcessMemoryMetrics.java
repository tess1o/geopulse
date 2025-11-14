package org.github.tess1o.geopulse.prometheus;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.*;

@ApplicationScoped
@Slf4j
public class NativeProcessMemoryMetrics {

    private final MeterRegistry registry;

    @Inject
    public NativeProcessMemoryMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void onStart(@Observes StartupEvent ev) {
        log.info("Registering native process memory metrics");
        registry.gauge("process_resident_memory_bytes", this, p -> p.getRss());
        registry.gauge("process_virtual_memory_bytes", this, p -> p.getVsize());
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
}