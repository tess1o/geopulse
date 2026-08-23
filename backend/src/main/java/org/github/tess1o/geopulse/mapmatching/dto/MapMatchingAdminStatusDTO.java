package org.github.tess1o.geopulse.mapmatching.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class MapMatchingAdminStatusDTO {
    private boolean enabled;
    private boolean configured;
    private Worker worker;
    private Backfill backfill;
    private Queue queue;
    private Diagnostics diagnostics;

    @Data
    @Builder
    public static class Worker {
        private boolean running;
        private String phase;
        private String trigger;
        private Instant startedAt;
        private Instant lastActivityAt;
        private String lastError;
    }

    @Data
    @Builder
    public static class Backfill {
        private boolean enabled;
        private long totalTrips;
        private long scannedTrips;
        private long remainingTrips;
        private double percent;
        private long totalUsers;
        private long completedUsers;
        private long remainingUsers;
    }

    @Data
    @Builder
    public static class Queue {
        private long queued;
        private long processing;
        private Instant oldestQueuedAt;
    }

    @Data
    @Builder
    public static class Diagnostics {
        private Instant lastWorkerCycleCompletedAt;
        private Instant oldestReconciliationCursorAt;
        private long pendingReconciliations;
        private Map<String, Long> targetsByStatus;
        private Map<String, Long> targetsBySource;
    }
}
