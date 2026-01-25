package org.github.tess1o.geopulse.shared.warmup;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.converters.StreamingTimelineConverter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Service that aggressively warms up timeline-related components on application startup
 * to eliminate first-request latency and prevent OOM errors in 512MB containers.
 * <p>
 * Strategy:
 * 1. Load real timeline data (30 days default) to pre-warm all caches and query plans
 * 2. Exercise full DTO conversion pipeline (geometry processing, coordinate extraction)
 * 3. Force garbage collection to free warmup objects before users arrive
 * <p>
 * Trade-off: Longer startup time (5-8s) for safe first-request handling.
 */
@ApplicationScoped
@Slf4j
public class TimelineWarmupService {

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject
    StreamingTimelineConverter converter;

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "geopulse.warmup.enabled", defaultValue = "true")
    boolean warmupEnabled;

    @ConfigProperty(name = "geopulse.warmup.sample-days", defaultValue = "30")
    int sampleDays;

    @ConfigProperty(name = "geopulse.warmup.max-items", defaultValue = "5000")
    int maxWarmupItems;

    void onStart(@Observes StartupEvent ev) {
        if (!warmupEnabled) {
            log.info("Warmup disabled via configuration");
            return;
        }

        log.info("Starting AGGRESSIVE timeline system warmup...");
        long startTime = System.currentTimeMillis();
        long memoryBefore = getUsedMemory();

        try {
            // 1. Find a real user to warm up with
            UUID warmupUserId = findWarmupUser();
            if (warmupUserId == null) {
                log.warn("No users found for warmup - skipping data loading");
                warmupWithDummyData();
                return;
            }

            warmupRealQueries(warmupUserId);

        } finally {
            long duration = System.currentTimeMillis() - startTime;
            long memoryAfter = getUsedMemory();
            long memoryUsed = memoryAfter - memoryBefore;

            log.info("Warmup completed in {}ms, memory used: {} MB",
                    duration, memoryUsed / 1024 / 1024);

            // CRITICAL: Force aggressive GC to free warmup objects
            forceGarbageCollection();
        }
    }

    private UUID findWarmupUser() {
        // Strategy: Find user with MODERATE amount of RECENT timeline data
        // Avoids:
        // - Users with no recent activity (no warmup benefit)
        // - Heavy users with 100K+ recent items (causes memory spike during warmup)
        //
        // Approach: Find user with 100-5000 timeline items in the LAST X days (stays + trips)
        // Orders by item_count DESC to maximize warmup effectiveness
        // This exercises the pipeline without consuming excessive memory

        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(sampleDays, ChronoUnit.DAYS);

        String sql = """
                SELECT * FROM (
                    SELECT u.id,
                           (SELECT COUNT(*) FROM timeline_stays
                            WHERE user_id = u.id
                            AND timestamp >= :startTime
                            AND timestamp <= :endTime) +
                           (SELECT COUNT(*) FROM timeline_trips
                            WHERE user_id = u.id
                            AND timestamp >= :startTime
                            AND timestamp <= :endTime) as item_count
                    FROM users u
                ) subquery
                WHERE item_count BETWEEN 10 AND 5000
                ORDER BY item_count DESC
                LIMIT 1
                """;

        try {
            Object[] result = (Object[]) entityManager.createNativeQuery(sql)
                .setParameter("startTime", startTime)
                .setParameter("endTime", endTime)
                .getSingleResult();
            UUID userId = UUID.fromString(result[0].toString());
            Long itemCount = ((Number) result[1]).longValue();
            log.info("Selected user {} for warmup ({} timeline items in last {} days)",
                userId, itemCount, sampleDays);
            return userId;
        } catch (Exception e) {
            log.warn("Could not find user with moderate data, falling back to any user");
            // Fallback: just pick first user
            List<UserEntity> users = UserEntity.listAll();
            return users.isEmpty() ? null : users.getFirst().getId();
        }
    }

    @Transactional
    public void warmupRealQueries(UUID userId) {
        log.debug("Warming up with REAL data for user {}", userId);

        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(sampleDays, ChronoUnit.DAYS);

        try {

            var stays = stayRepository.findByUserIdAndTimeRangeWithExpansion(
                    userId, startTime, endTime
            );
            log.debug("Loaded {} stays for warmup", stays.size());

            var trips = tripRepository.findByUserIdAndTimeRangeWithExpansion(
                    userId, startTime, endTime
            );
            log.debug("Loaded {} trips for warmup", trips.size());

            var gaps = dataGapRepository.findByUserIdAndTimeRangeWithExpansion(
                    userId, startTime, endTime
            );
            log.debug("Loaded {} data gaps for warmup", gaps.size());

            // Limit conversion to maxWarmupItems to prevent memory spikes
            // Full query execution already warmed up the pipeline
            int convertedCount = 0;
            int totalItems = stays.size() + trips.size();

            if (totalItems > maxWarmupItems) {
                log.info("Loaded {} items, limiting DTO conversion to {} to prevent memory spike",
                        totalItems, maxWarmupItems);
            }

            // Convert to DTOs (warms up geometry conversion, coordinate extraction)
            for (var stay : stays) {
                if (convertedCount >= maxWarmupItems) break;
                converter.convertStayEntityToDto(stay);
                convertedCount++;
            }
            for (var trip : trips) {
                if (convertedCount >= maxWarmupItems) break;
                converter.convertTripEntityToDto(trip);
                convertedCount++;
            }

            log.debug("Converted {} entities to DTOs during warmup", convertedCount);

        } catch (Exception e) {
            log.error("Warmup query execution failed (non-critical): {}", e.getMessage(), e);
        }
    }

    private void warmupWithDummyData() {
        // Fallback: minimal warmup with dummy queries
        log.debug("Using dummy data warmup (no real users)");

        Instant futureTime = Instant.now().plus(36500, ChronoUnit.DAYS);
        UUID dummyUserId = UUID.fromString("00000000-0000-0000-0000-000000000000");

        try {
            stayRepository.findByUserIdAndTimeRangeWithExpansion(
                    dummyUserId, futureTime, futureTime
            );
            tripRepository.findByUserIdAndTimeRangeWithExpansion(
                    dummyUserId, futureTime, futureTime
            );
            dataGapRepository.findByUserIdAndTimeRangeWithExpansion(
                    dummyUserId, futureTime, futureTime
            );
        } catch (Exception e) {
            log.warn("Dummy warmup failed: {}", e.getMessage());
        }
    }

    /**
     * CRITICAL: Force aggressive garbage collection after warmup.
     * Frees temporary objects created during warmup before real users arrive.
     */
    private void forceGarbageCollection() {
        long beforeGC = getUsedMemory();
        log.info("Forcing GC after warmup (current heap: {} MB)...",
                beforeGC / 1024 / 1024);

        System.gc();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long afterGC = getUsedMemory();
        long freedMemory = beforeGC - afterGC;
        log.info("Post-warmup GC freed {} MB (heap now: {} MB)",
                freedMemory / 1024 / 1024, afterGC / 1024 / 1024);
    }

    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
