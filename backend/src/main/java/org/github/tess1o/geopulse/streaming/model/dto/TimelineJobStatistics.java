package org.github.tess1o.geopulse.streaming.model.dto;

public record TimelineJobStatistics(
        long totalJobs,
        long queuedJobs,
        long runningJobs,
        long completedJobs,
        long failedJobs,
        long activeUserJobs
) {
}
