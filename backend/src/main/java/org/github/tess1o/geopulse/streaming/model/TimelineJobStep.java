package org.github.tess1o.geopulse.streaming.model;

import org.github.tess1o.geopulse.shared.api.MessageDescriptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One step a timeline job went through, such as processing GPS points or geocoding stays: when it ran, what it
 * counted and how much memory the backend used. Updated by the job while the step runs and by the memory sampler,
 * read by API requests at any time.
 */
public class TimelineJobStep {

    private final String phase;
    private final MessageDescriptor title;
    private final Instant startedAt;
    private volatile Instant endedAt;
    private volatile boolean failed;
    private final Map<String, Object> counters = new ConcurrentHashMap<>();
    private volatile TimelineJobMemory memoryEnd;
    private volatile TimelineJobMemory memoryMax;

    public TimelineJobStep(String phase, MessageDescriptor title, Instant startedAt, TimelineJobMemory memoryAtStart) {
        this.phase = phase;
        this.title = title;
        this.startedAt = startedAt;
        this.memoryMax = memoryAtStart;
    }

    /** Short name of the step, such as {@code geocoding}; the page translates it, {@link #getTitle()} is its fallback. */
    public String getPhase() {
        return phase;
    }

    public MessageDescriptor getTitle() {
        return title;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    /** Null while the step runs. */
    public Instant getEndedAt() {
        return endedAt;
    }

    /** How long the step ran, or has run so far. */
    public long getDurationMs() {
        Instant end = endedAt != null ? endedAt : Instant.now();
        return Duration.between(startedAt, end).toMillis();
    }

    /** True for the step a failed job stopped in. */
    public boolean isFailed() {
        return failed;
    }

    /** The values the job reported during this step, such as points processed or stays geocoded from the cache. */
    public Map<String, Object> getCounters() {
        return Collections.unmodifiableMap(counters);
    }

    /** Memory when the step ended; null while it runs. */
    public TimelineJobMemory getMemoryEnd() {
        return memoryEnd;
    }

    /** The highest memory seen while the step ran (sampled about once a second, and at its start and end). */
    public TimelineJobMemory getMemoryMax() {
        return memoryMax;
    }

    public void addCounters(Map<String, Object> values) {
        values.forEach((key, value) -> {
            if (key != null && value != null) {
                counters.put(key, value);
            }
        });
    }

    public synchronized void sample(TimelineJobMemory memory) {
        if (memory != null) {
            memoryMax = memory.max(memoryMax);
        }
    }

    public synchronized void end(Instant at, TimelineJobMemory memory, boolean failedHere) {
        if (endedAt != null) {
            return;
        }
        sample(memory);
        memoryEnd = memory;
        failed = failedHere;
        endedAt = at;
    }
}
