package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.Session;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Opt-in checkpoints: no collection walks, SQL, forced GC or retained per-job state. */
@ApplicationScoped
@Slf4j
public class TimelineMemoryDiagnostics {

    @ConfigProperty(name = "geopulse.timeline.memory-diagnostics.enabled", defaultValue = "false")
    boolean enabled;

    @Inject
    EntityManager entityManager;

    @Inject
    TransactionSynchronizationRegistry transactions;

    public boolean isEnabled() {
        return enabled;
    }

    public void checkpoint(UUID userId, UUID jobId, String stage) {
        checkpoint(userId, jobId, stage, Map.of());
    }

    public void checkpoint(UUID userId, UUID jobId, String stage, Map<String, ? extends Number> counts) {
        if (!enabled) {
            return;
        }
        try {
            log.info("Timeline memory diagnostics userId={} jobId={} stage={} counts={} memory={}",
                    userId, jobId, stage, counts, snapshot());
        } catch (RuntimeException e) {
            // An unavailable measurement must not change timeline generation or transaction outcome.
            log.debug("Could not capture timeline memory at {}", stage, e);
        }
    }

    public void watchTransaction(UUID userId, UUID jobId) {
        if (!enabled) {
            return;
        }
        try {
            transactions.registerInterposedSynchronization(new Synchronization() {
                @Override
                public void beforeCompletion() {
                    checkpoint(userId, jobId, "before_commit");
                }

                @Override
                public void afterCompletion(int status) {
                    checkpoint(userId, jobId, "after_transaction", Map.of("transactionStatus", status));
                }
            });
        } catch (RuntimeException e) {
            log.debug("Could not register timeline transaction memory checkpoints", e);
        }
    }

    Map<String, Long> snapshot() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("heapUsedBytes", runtime.totalMemory() - runtime.freeMemory());
        values.put("heapCommittedBytes", runtime.totalMemory());
        values.put("heapMaxBytes", runtime.maxMemory());
        Map<String, Long> process = readFields(Path.of("/proc/self/status"), "VmRSS", "VmHWM", "RssAnon", "RssFile");
        values.put("rssBytes", process.getOrDefault("VmRSS", -1L));
        values.put("rssPeakBytes", process.getOrDefault("VmHWM", -1L));
        values.put("rssAnonBytes", process.getOrDefault("RssAnon", -1L));
        values.put("rssFileBytes", process.getOrDefault("RssFile", -1L));
        Path cgroup = Path.of("/sys/fs/cgroup");
        values.put("cgroupCurrentBytes", readLong(cgroup.resolve("memory.current")));
        values.put("cgroupPeakBytes", readLong(cgroup.resolve("memory.peak")));
        values.put("cgroupLimitBytes", readLong(cgroup.resolve("memory.max")));
        Map<String, Long> memory = readFields(cgroup.resolve("memory.stat"), "anon", "file", "kernel");
        values.put("cgroupAnonBytes", memory.getOrDefault("anon", -1L));
        values.put("cgroupFileBytes", memory.getOrDefault("file", -1L));
        values.put("cgroupKernelBytes", memory.getOrDefault("kernel", -1L));
        long entities = -1;
        long collections = -1;
        try {
            var statistics = entityManager.unwrap(Session.class).getStatistics();
            entities = statistics.getEntityCount();
            collections = statistics.getCollectionCount();
        } catch (RuntimeException e) {
            // The session may already be closed after transaction completion.
            log.debug("Hibernate session counts unavailable", e);
        }
        values.put("managedEntities", entities);
        values.put("managedCollections", collections);
        return values;
    }

    static Map<String, Long> readFields(Path path, String... names) {
        Set<String> wanted = Set.of(names);
        Map<String, Long> values = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.trim().split("\\s+");
                String name = fields[0].replace(":", "");
                if (wanted.contains(name) && fields.length >= 2) {
                    long multiplier = fields.length >= 3 && "kB".equals(fields[2]) ? 1024 : 1;
                    values.put(name, Long.parseLong(fields[1]) * multiplier);
                }
            }
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            log.debug("Memory counters unavailable at {}", path);
        }
        return values;
    }

    static long readLong(Path path) {
        try {
            return Long.parseLong(Files.readString(path).trim());
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            return -1;
        }
    }
}
