package org.github.tess1o.geopulse.prometheus;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class GpsPointsMetrics {

    private final AtomicLong totalGpsPoints = new AtomicLong();
    private final AtomicLong lastGpsTimestamp = new AtomicLong();
    private final Map<String, AtomicLong> gpsPerUser = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastestGpsPerUser = new ConcurrentHashMap<>();

    @Inject
    MeterRegistry registry;

    @Inject
    GpsPointRepository gpsRepository;

    @Inject
    UserRepository userRepository;

    void onStart(@Observes StartupEvent ev) {
        setMetricValues();
        Gauge.builder("gps_points_total", totalGpsPoints, AtomicLong::get)
                .description("Total number of GPS points for all users")
                .register(registry);
        Gauge.builder("gps_last_timestamp", lastGpsTimestamp, AtomicLong::get)
                .description("Unix timestamp of the last GPS point received")
                .register(registry);
    }

    @Scheduled(every = "10m")
    void refreshTotalGps() {
        setMetricValues();
    }

    private void setMetricValues() {
        totalGpsPoints.set(gpsRepository.count());
        gpsRepository.findLatest().ifPresent(
                latest -> lastGpsTimestamp.set(latest.getTimestamp().toEpochMilli() / 1000)
        );

        for (UserEntity user : userRepository.findAll().stream().toList()) {
            // GPS points per user
            AtomicLong countHolder = gpsPerUser.computeIfAbsent(user.getEmail(), e -> {
                AtomicLong h = new AtomicLong();
                Gauge.builder("gps_points_total", h, AtomicLong::get)
                        .tag("user", e)
                        .description("Total number of GPS points for this user")
                        .register(registry);
                return h;
            });
            countHolder.set(gpsRepository.countByUser(user.getId()));

            // Latest GPS timestamp per user
            AtomicLong latestTimestampHolder = lastestGpsPerUser.computeIfAbsent(user.getEmail(), e -> {
                AtomicLong h = new AtomicLong();
                Gauge.builder("gps_last_timestamp", h, AtomicLong::get)
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
