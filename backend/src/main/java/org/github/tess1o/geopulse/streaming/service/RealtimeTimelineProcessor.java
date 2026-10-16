package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.streaming.config.TimelineConfigurationProvider;
import org.github.tess1o.geopulse.streaming.model.TimelineRebuildRequest;
import org.github.tess1o.geopulse.streaming.repository.TimelineRebuildRequestRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * One real-time pass for one user.
 * <p>
 * The timeline only changes with its data: the engine works on GPS timestamps, never on the clock. So a user with no
 * pending rebuild request only gets the ongoing data gap created or extended, and a user with one gets the timeline
 * rebuilt from the request onwards. Every change that can alter the timeline writes a request in its own transaction
 * (see {@link TimelineRebuildRequestRepository}).
 */
@ApplicationScoped
@Slf4j
public class RealtimeTimelineProcessor {

    /**
     * The job regenerates the same recent stretch again and again, so it restarts where a partial regeneration
     * matches a full one (see {@link StreamingTimelineGenerationService.RestartPoint#BEFORE_PREVIOUS_STAY}).
     */
    static final StreamingTimelineGenerationService.RestartPoint RESTART_POINT =
            StreamingTimelineGenerationService.RestartPoint.BEFORE_PREVIOUS_STAY;

    public enum Outcome {
        /** Rebuilt from the pending request onwards. */
        REBUILT("rebuilt"),
        /** The pending request keeps failing and waits for its retry; only the latest stays were rebuilt. */
        REBUILT_LATEST_ONLY("rebuilt_latest_only"),
        /** Nothing new: only the ongoing data gap was maintained. */
        GAP_ONLY("gap_only"),
        /** Another generation holds the user's timeline; the next pass picks the user up. */
        SKIPPED_BUSY("skipped_busy"),
        /** A timeline job (import, manual rebuild, settings change) is running for the user. */
        SKIPPED_ACTIVE_JOB("skipped_active_job");

        private final String metricResult;

        Outcome(String metricResult) {
            this.metricResult = metricResult;
        }

        public String metricResult() {
            return metricResult;
        }
    }

    @Inject
    TimelineUserLock userLock;

    @Inject
    TimelineRebuildRequestRepository rebuildRequestRepository;

    @Inject
    StreamingTimelineGenerationService generationService;

    @Inject
    StreamingDataGapService dataGapService;

    @Inject
    TimelineConfigurationProvider configurationProvider;

    @Inject
    TimelineJobProgressService jobProgressService;

    @Transactional
    public Outcome process(UUID userId) {
        if (jobProgressService.getUserActiveJob(userId).isPresent()) {
            return Outcome.SKIPPED_ACTIVE_JOB;
        }
        // Never wait for a long generation (an import, a full rebuild): with only a few real-time workers, waiting
        // here would stall every other user.
        if (!userLock.tryLock(userId)) {
            return Outcome.SKIPPED_BUSY;
        }

        Instant now = Instant.now();
        Optional<TimelineRebuildRequest> pending = rebuildRequestRepository.find(userId);
        if (pending.isEmpty()) {
            dataGapService.checkAndCreateOngoingDataGap(userId, configurationProvider.getConfigurationForUser(userId));
            return Outcome.GAP_ONLY;
        }

        TimelineRebuildRequest request = pending.get();
        if (request.isParked(now)) {
            log.warn("Timeline rebuild for user {} from {} failed {} times (last at {}); rebuilding only the latest "
                            + "stays until it is retried", userId, request.rebuildFrom(), request.failedAttempts(),
                    request.lastFailedAt());
            generationService.generateTimelineFromTimestamp(userId, now, null, "realtime", false, RESTART_POINT);
            return Outcome.REBUILT_LATEST_ONLY;
        }

        generationService.generateTimelineFromTimestamp(userId, request.rebuildFrom(), null, "realtime", true,
                RESTART_POINT);
        return Outcome.REBUILT;
    }
}
