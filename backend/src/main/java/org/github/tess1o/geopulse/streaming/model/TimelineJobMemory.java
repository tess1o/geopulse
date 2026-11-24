package org.github.tess1o.geopulse.streaming.model;

/**
 * Memory of the backend process at one moment, in bytes; -1 where the platform does not expose a value (container
 * memory outside a cgroup v2 container, process memory outside Linux).
 *
 * @param heapUsedBytes  Java heap in use
 * @param processBytes   resident memory of the process (RSS)
 * @param containerBytes memory charged to the container (cgroup {@code memory.current})
 */
public record TimelineJobMemory(long heapUsedBytes, long processBytes, long containerBytes) {

    /** The larger of each value. */
    public TimelineJobMemory max(TimelineJobMemory other) {
        if (other == null) {
            return this;
        }
        return new TimelineJobMemory(Math.max(heapUsedBytes, other.heapUsedBytes),
                Math.max(processBytes, other.processBytes), Math.max(containerBytes, other.containerBytes));
    }
}
