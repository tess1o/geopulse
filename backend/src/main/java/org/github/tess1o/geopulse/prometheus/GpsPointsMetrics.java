package org.github.tess1o.geopulse.prometheus;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class GpsPointsMetrics {

    private final AtomicLong totalGpsPoints = new AtomicLong();
    private final AtomicLong lastGpsTimestamp = new AtomicLong();
    private final AtomicLong gpsPointsLast24h = new AtomicLong();
    private final AtomicLong avgGpsPointsPerUser = new AtomicLong();
    private final Map<String, AtomicLong> gpsPerUser = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastestGpsPerUser = new ConcurrentHashMap<>();

    @Inject
    MeterRegistry registry;

    @Inject
    GpsPointRepository gpsRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    void onStart(@Observes StartupEvent ev) {
        // Set all metric values before registering gauges
        setMetricValues();

        // Register overall metrics (without user tags)
        Gauge.builder("gps_points_total", totalGpsPoints, AtomicLong::get)
                .description("Total number of GPS points for all users")
                .register(registry);
        Gauge.builder("gps_last_timestamp", lastGpsTimestamp, AtomicLong::get)
                .description("Unix timestamp of the last GPS point received")
                .register(registry);
        Gauge.builder("gps_points_last_24h", gpsPointsLast24h, AtomicLong::get)
                .description("Number of GPS points added in the last 24 hours")
                .register(registry);
        Gauge.builder("gps_avg_points_per_user", avgGpsPointsPerUser, AtomicLong::get)
                .description("Average number of GPS points per user (among users with GPS data)")
                .register(registry);
    }

    @Scheduled(every = "10m")
    void refreshTotalGps() {
        setMetricValues();
    }

    private void setMetricValues() {
        long total = gpsRepository.count();
        totalGpsPoints.set(total);
        gpsRepository.findLatest().ifPresent(
                latest -> lastGpsTimestamp.set(latest.getTimestamp().toEpochMilli() / 1000)
        );

        // Count GPS points in last 24 hours
        Instant last24h = Instant.now().minus(24, ChronoUnit.HOURS);
        Long pointsLast24h = (Long) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM gps_points WHERE timestamp >= :threshold")
                .setParameter("threshold", last24h)
                .getSingleResult();
        gpsPointsLast24h.set(pointsLast24h);

        // Calculate average GPS points per user (among users with GPS data)
        Long usersWithData = (Long) entityManager.createNativeQuery(
                        "SELECT COUNT(DISTINCT user_id) FROM gps_points")
                .getSingleResult();
        if (usersWithData > 0) {
            avgGpsPointsPerUser.set(total / usersWithData);
        } else {
            avgGpsPointsPerUser.set(0);
        }

        for (UserEntity user : userRepository.findAll().stream().toList()) {
            // GPS points per user
            AtomicLong countHolder = gpsPerUser.computeIfAbsent(user.getEmail(), e -> {
                AtomicLong h = new AtomicLong();
                Gauge.builder("gps_points_per_user_total", h, AtomicLong::get)
                        .tag("user", e)
                        .description("Total number of GPS points for this user")
                        .register(registry);
                return h;
            });
            countHolder.set(gpsRepository.countByUser(user.getId()));

            // Latest GPS timestamp per user
            AtomicLong latestTimestampHolder = lastestGpsPerUser.computeIfAbsent(user.getEmail(), e -> {
                AtomicLong h = new AtomicLong();
                Gauge.builder("gps_last_timestamp_per_user", h, AtomicLong::get)
                        .tag("user", e)
                        .description("Unix timestamp of last GPS point for this user")
                        .register(registry);
                return h;
            });
            gpsRepository.findLatest(user.getId()).map(GpsPointEntity::getTimestamp).ifPresent(
                    t -> latestTimestampHolder.set(t.toEpochMilli() / 1000)
            );
        }
    }
}
