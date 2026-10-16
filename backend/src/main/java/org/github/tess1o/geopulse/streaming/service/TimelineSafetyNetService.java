package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.prometheus.GeoPulseWorkloadMetrics;
import org.github.tess1o.geopulse.streaming.repository.TimelineRebuildRequestRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Rebuilds the recent part of a user's timeline and compares it with what was there.
 * <p>
 * Every change that can alter a timeline asks for its own rebuild, so the rebuilt part should come out identical. A
 * difference means a change slipped through, or the real-time job's partial rebuilds disagreed with a clean one; the
 * rebuild fixes it either way, and the difference is logged and counted so it can be investigated.
 */
@ApplicationScoped
@Slf4j
public class TimelineSafetyNetService {

    private static final int MAX_LOGGED_DIFFERENCES = 20;
    private static final StreamingTimelineGenerationService.RestartPoint RESTART_POINT =
            StreamingTimelineGenerationService.RestartPoint.BEFORE_PREVIOUS_STAY;

    public enum Outcome {
        /** The rebuilt part matched the existing timeline. */
        UNCHANGED("unchanged"),
        /** The rebuilt part differed; the timeline is now corrected. */
        CORRECTED("corrected"),
        /** The user was busy or had a rebuild pending; a request covering the window was left for the real-time job. */
        DEFERRED("deferred");

        private final String metricResult;

        Outcome(String metricResult) {
            this.metricResult = metricResult;
        }

        public String metricResult() {
            return metricResult;
        }
    }

    @Inject
    EntityManager entityManager;

    @Inject
    TimelineUserLock userLock;

    @Inject
    TimelineRebuildRequestRepository rebuildRequestRepository;

    @Inject
    StreamingTimelineGenerationService generationService;

    @Inject
    TimelineJobProgressService jobProgressService;

    @Inject
    GeoPulseWorkloadMetrics workloadMetrics;

    /** Users with GPS points at or after {@code since}. */
    public List<UUID> usersWithPointsSince(Instant since) {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery(
                        "SELECT DISTINCT user_id FROM gps_points WHERE timestamp >= ?1")
                .setParameter(1, since)
                .getResultList();
        return rows.stream().map(row -> row instanceof UUID uuid ? uuid : UUID.fromString(row.toString())).toList();
    }

    /** Rebuilds the user's timeline from {@code from} and compares the result with what was there. */
    @Transactional
    public Outcome verifyUser(UUID userId, Instant from) {
        if (jobProgressService.getUserActiveJob(userId).isPresent()
                || !userLock.tryLock(userId)
                || rebuildRequestRepository.find(userId).isPresent()) {
            // Leave it to the real-time job; the comparison is skipped but the window is still rebuilt.
            rebuildRequestRepository.request(userId, from);
            count(Outcome.DEFERRED);
            return Outcome.DEFERRED;
        }

        // Compare everything the generation replaces. It restarts where the real-time job does, so a difference
        // points at a missed change rather than at where the two restart.
        Instant comparedFrom = generationService.findRestartPoint(userId, from, RESTART_POINT)
                .orElse(StreamingTimelineGenerationService.DEFAULT_START_DATE);
        List<String> before = snapshot(userId, comparedFrom);
        generationService.generateTimelineFromTimestamp(userId, from, null, "safety-net", true, RESTART_POINT);
        entityManager.flush();
        List<String> after = snapshot(userId, comparedFrom);

        if (before.equals(after)) {
            count(Outcome.UNCHANGED);
            return Outcome.UNCHANGED;
        }
        log.warn("Timeline safety net corrected the timeline of user {} from {}:{}", userId, comparedFrom,
                describeDifferences(before, after));
        count(Outcome.CORRECTED);
        return Outcome.CORRECTED;
    }

    /**
     * The stays, trips and data gaps starting at or after {@code from}, in a comparable form. Ids change on every
     * rebuild and the ongoing gap ends at "now", so neither is part of it.
     */
    List<String> snapshot(UUID userId, Instant from) {
        Instant latestPoint = toInstant(entityManager.createNativeQuery(
                        "SELECT max(timestamp) FROM gps_points WHERE user_id = ?1")
                .setParameter(1, userId)
                .getSingleResult());
        List<String> items = new ArrayList<>();
        for (Object row : entityManager.createNativeQuery("""
                        SELECT timestamp, stay_duration, location_name, favorite_id, geocoding_id
                        FROM timeline_stays WHERE user_id = ?1 AND timestamp >= ?2 ORDER BY timestamp
                        """)
                .setParameter(1, userId).setParameter(2, from).getResultList()) {
            Object[] stay = (Object[]) row;
            items.add("stay " + toInstant(stay[0]) + " " + stay[1] + "s '" + stay[2] + "' favorite=" + stay[3]
                    + " geocoding=" + stay[4]);
        }
        for (Object row : entityManager.createNativeQuery("""
                        SELECT timestamp, trip_duration, distance_meters, movement_type
                        FROM timeline_trips WHERE user_id = ?1 AND timestamp >= ?2 ORDER BY timestamp
                        """)
                .setParameter(1, userId).setParameter(2, from).getResultList()) {
            Object[] trip = (Object[]) row;
            items.add("trip " + toInstant(trip[0]) + " " + trip[1] + "s " + trip[2] + "m " + trip[3]);
        }
        for (Object row : entityManager.createNativeQuery("""
                        SELECT start_time, end_time
                        FROM timeline_data_gaps WHERE user_id = ?1 AND start_time >= ?2 ORDER BY start_time
                        """)
                .setParameter(1, userId).setParameter(2, from).getResultList()) {
            Object[] gap = (Object[]) row;
            Instant start = toInstant(gap[0]);
            boolean ongoing = Objects.equals(start, latestPoint);
            items.add("gap " + start + " .. " + (ongoing ? "ongoing" : toInstant(gap[1])));
        }
        return items;
    }

    private static String describeDifferences(List<String> before, List<String> after) {
        Set<String> removed = new LinkedHashSet<>(before);
        after.forEach(removed::remove);
        Set<String> added = new LinkedHashSet<>(after);
        before.forEach(added::remove);
        StringBuilder text = new StringBuilder();
        removed.stream().limit(MAX_LOGGED_DIFFERENCES).forEach(item -> text.append("\n  - ").append(item));
        added.stream().limit(MAX_LOGGED_DIFFERENCES).forEach(item -> text.append("\n  + ").append(item));
        return text.toString();
    }

    private void count(Outcome outcome) {
        if (workloadMetrics != null) {
            workloadMetrics.increment("geopulse.timeline.safety_net.users",
                    "component", "timeline",
                    "result", outcome.metricResult());
        }
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        return null;
    }
}
