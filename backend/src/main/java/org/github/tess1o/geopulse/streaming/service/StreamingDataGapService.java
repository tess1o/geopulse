package org.github.tess1o.geopulse.streaming.service;

import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.config.TimelineConfig;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service responsible for data gap detection logic in streaming timeline processing.
 * Provides common methods for validating data gaps based on timeline configuration.
 */
@ApplicationScoped
@Slf4j
public class StreamingDataGapService {

    @Inject
    GpsPointRepository gpsPointRepository;

    @Inject
    TimelineDataGapRepository timelineDataGapRepository;

    /**
     * Comprehensive check to determine if a data gap should be created.
     * Validates both threshold and minimum duration requirements.
     *
     * @param config    timeline configuration
     * @param startTime gap start time
     * @param endTime   gap end time
     * @return true if a data gap should be created for this time period
     */
    public boolean shouldCreateDataGap(TimelineConfig config, Instant startTime, Instant endTime) {
        Duration gapDuration = Duration.between(startTime, endTime);

        // First check if it exceeds the threshold
        boolean exceedsThreshold = exceedsGapThreshold(config, gapDuration);

        if (!exceedsThreshold) {
            log.trace("Gap from {} to {} ({} seconds = {}min) does not exceed threshold",
                    startTime, endTime, gapDuration.getSeconds(), gapDuration.toMinutes());
            return false;
        }

        // Then check if it meets minimum duration
        boolean meetsMinDuration = meetsMinimumDuration(config, gapDuration);

        if (!meetsMinDuration) {
            log.trace("Gap from {} to {} ({} seconds = {}min) does not meet minimum duration",
                    startTime, endTime, gapDuration.getSeconds(), gapDuration.toMinutes());
            return false;
        }

        log.debug("Gap from {} to {} ({} seconds = {}min) meets criteria for creation/extension",
                startTime, endTime, gapDuration.getSeconds(), gapDuration.toMinutes());
        return true;
    }

    /**
     * Check if there should be an ongoing data gap from the last GPS point to now.
     * Create or extend the gap if needed based on timeline configuration.
     *
     * @param userId user identifier
     * @param config timeline configuration containing gap thresholds
     */
    @Transactional
    public void checkAndCreateOngoingDataGap(UUID userId, TimelineConfig config) {
        log.trace("Checking for ongoing data gap for user {}", userId);

        // Get the latest GPS point for this user
        Optional<GpsPointEntity> lastGpsPoint = gpsPointRepository.find("user.id = :userId order by timestamp desc",
                Parameters.with("userId", userId)).firstResultOptional();

        if (lastGpsPoint.isEmpty()) {
            log.debug("No GPS points found for user {}, no ongoing gap needed", userId);
            return;
        }

        Instant now = Instant.now();
        Instant lastGpsTime = lastGpsPoint.get().getTimestamp();

        // Use service to check if gap should be created
        if (shouldCreateDataGap(config, lastGpsTime, now)) {
            // Check if there's already a gap starting from this GPS point
            Optional<TimelineDataGapEntity> existingGap = timelineDataGapRepository.findLatestByUserId(userId);

            if (existingGap.isPresent() && existingGap.get().getStartTime().equals(lastGpsTime)) {
                // Extend existing gap
                TimelineDataGapEntity gap = existingGap.get();
                long oldDuration = gap.getDurationSeconds();
                gap.setEndTime(now);
                gap.setDurationSeconds(Duration.between(gap.getStartTime(), gap.getEndTime()).getSeconds());
                long newDuration = gap.getDurationSeconds();

                // Only log if duration changed significantly (more than 1 minute)
                if (Math.abs(newDuration - oldDuration) > 60) {
                    log.info("Extended data gap for user {} from {}min to {}min (start: {})",
                            userId, oldDuration / 60, newDuration / 60, gap.getStartTime());
                } else {
                    log.debug("Data gap for user {} remains at ~{}min (no significant change)",
                            userId, newDuration / 60);
                }
            } else {
                // Create new gap
                TimelineDataGapEntity newGap = new TimelineDataGapEntity();
                UserEntity user = UserEntity.findById(userId);
                newGap.setUser(user);
                newGap.setStartTime(lastGpsTime);
                newGap.setEndTime(now);
                newGap.setDurationSeconds(Duration.between(newGap.getStartTime(), newGap.getEndTime()).getSeconds());
                timelineDataGapRepository.persist(newGap);
                log.info("Created new data gap for user {} - start: {}, duration: {}min",
                        userId, lastGpsTime, Duration.between(lastGpsTime, now).toMinutes());
            }
        } else {
            log.debug("No ongoing data gap needed for user {} - current gap doesn't meet criteria", userId);
        }
    }

    /**
     * Determines if a time gap between two points should be considered a data gap
     * based on the configured threshold.
     *
     * @param config         timeline configuration containing gap thresholds
     * @param timeDifference duration between two GPS points
     * @return true if the gap exceeds the configured threshold
     */
    private boolean exceedsGapThreshold(TimelineConfig config, Duration timeDifference) {
        Integer gapThresholdSeconds = config.getDataGapThresholdSeconds();

        if (gapThresholdSeconds == null) {
            log.debug("Data gap threshold not configured, using default 1 hour");
            return timeDifference.compareTo(Duration.ofHours(1)) > 0;
        }

        return timeDifference.getSeconds() > gapThresholdSeconds;
    }

    /**
     * Determines if a data gap meets the minimum duration requirement to be recorded.
     *
     * @param config      timeline configuration containing minimum gap duration
     * @param gapDuration duration of the detected gap
     * @return true if the gap meets the minimum duration requirement
     */
    private boolean meetsMinimumDuration(TimelineConfig config, Duration gapDuration) {
        Integer minGapDurationSeconds = config.getDataGapMinDurationSeconds();

        if (minGapDurationSeconds == null) {
            log.debug("Minimum gap duration not configured, allowing all gaps");
            return true;
        }

        return gapDuration.getSeconds() >= minGapDurationSeconds;
    }
}