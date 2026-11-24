package org.github.tess1o.geopulse.streaming.service;

import org.github.tess1o.geopulse.streaming.model.TimelineJobMemory;
import org.github.tess1o.geopulse.streaming.model.TimelineJobResources;

import java.nio.file.Path;
import java.util.Map;

/**
 * Reads the backend's memory without touching the database or the persistence context, so any thread can call it:
 * the Java heap from {@link Runtime}, process memory from {@code /proc/self/status} and container memory from the
 * cgroup v2 files. A value the platform does not expose is -1.
 */
final class ProcessMemory {

    private static final Path PROCESS_STATUS = Path.of("/proc/self/status");
    private static final Path CGROUP = Path.of("/sys/fs/cgroup");

    private ProcessMemory() {
    }

    static TimelineJobMemory sample() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Long> process = TimelineMemoryDiagnostics.readFields(PROCESS_STATUS, "VmRSS");
        return new TimelineJobMemory(runtime.totalMemory() - runtime.freeMemory(), process.getOrDefault("VmRSS", -1L),
                TimelineMemoryDiagnostics.readLong(CGROUP.resolve("memory.current")));
    }

    /** Limits and the current kernel peaks; the end peaks start equal to the start peaks. */
    static TimelineJobResources resources() {
        long processPeak = processPeak();
        long containerPeak = containerPeak();
        return new TimelineJobResources(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory(),
                TimelineMemoryDiagnostics.readLong(CGROUP.resolve("memory.max")), processPeak, processPeak,
                containerPeak, containerPeak);
    }

    static long processPeak() {
        return TimelineMemoryDiagnostics.readFields(PROCESS_STATUS, "VmHWM").getOrDefault("VmHWM", -1L);
    }

    static long containerPeak() {
        return TimelineMemoryDiagnostics.readLong(CGROUP.resolve("memory.peak"));
    }
}
