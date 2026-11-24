package org.github.tess1o.geopulse.streaming.model;

/**
 * Limits and peaks of the backend around one job, in bytes (-1 where unavailable). The kernel peaks only grow from
 * the start of the process or container, so they are taken at the start and at the end of the job: a peak that rose
 * in between was reached during the job.
 *
 * @param cpuCount                processors available to the backend
 * @param heapMaxBytes            maximum Java heap
 * @param containerLimitBytes     container memory limit (cgroup {@code memory.max}); -1 when unlimited
 * @param processPeakAtStartBytes process memory peak (VmHWM) when the job started
 * @param processPeakAtEndBytes   process memory peak when the job ended
 * @param containerPeakAtStartBytes container memory peak (cgroup {@code memory.peak}) when the job started
 * @param containerPeakAtEndBytes container memory peak when the job ended
 */
public record TimelineJobResources(int cpuCount, long heapMaxBytes, long containerLimitBytes,
                                   long processPeakAtStartBytes, long processPeakAtEndBytes,
                                   long containerPeakAtStartBytes, long containerPeakAtEndBytes) {

    public TimelineJobResources withEndPeaks(long processPeak, long containerPeak) {
        return new TimelineJobResources(cpuCount, heapMaxBytes, containerLimitBytes, processPeakAtStartBytes,
                processPeak, containerPeakAtStartBytes, containerPeak);
    }
}
