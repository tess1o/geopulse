package org.github.tess1o.geopulse.prometheus;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
@Slf4j
public class UserMetrics {

    private final AtomicLong usersTotal = new AtomicLong();
    private final AtomicLong usersActiveLast24h = new AtomicLong();
    private final AtomicLong usersActiveLast7d = new AtomicLong();
    private final AtomicLong usersWithGpsData = new AtomicLong();

    @Inject
    MeterRegistry registry;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "geopulse.prometheus.enabled", defaultValue = "true")
    boolean prometheusEnabled;

    @ConfigProperty(name = "geopulse.prometheus.user-metrics.enabled", defaultValue = "true")
    boolean userMetricsEnabled;

    void onStart(@Observes StartupEvent ev) {
        if (!prometheusEnabled || !userMetricsEnabled) {
            log.info("User metrics disabled");
            return;
        }

        try {

            setMetricValues();

            Gauge.builder("users_total", usersTotal, AtomicLong::get)
                    .description("Total number of users")
                    .register(registry);
            Gauge.builder("users_active_last_24h", usersActiveLast24h, AtomicLong::get)
                    .description("Number of users with GPS points in last 24 hours")
                    .register(registry);
            Gauge.builder("users_active_last_7d", usersActiveLast7d, AtomicLong::get)
                    .description("Number of users with GPS points in last 7 days")
                    .register(registry);
            Gauge.builder("users_with_gps_data", usersWithGpsData, AtomicLong::get)
                    .description("Number of users who have at least one GPS point")
                    .register(registry);
        } catch (Exception e) {
            log.error("Failed to initialize User metrics", e);
        }
    }

    @Scheduled(every = "${geopulse.prometheus.refresh-interval:10m}")
    void refreshTotals() {
        if (!prometheusEnabled || !userMetricsEnabled) {
            return;
        }
        setMetricValues();
    }

    private void setMetricValues() {
        usersTotal.set(userRepository.count());

        // Count users with GPS data in last 24 hours
        Instant last24h = Instant.now().minus(24, ChronoUnit.HOURS);
        Long activeUsersLast24h = (Long) entityManager.createNativeQuery(
                        "SELECT COUNT(DISTINCT user_id) FROM gps_points WHERE timestamp >= :threshold")
                .setParameter("threshold", last24h)
                .getSingleResult();
        usersActiveLast24h.set(activeUsersLast24h);

        // Count users with GPS data in last 7 days
        Instant last7d = Instant.now().minus(7, ChronoUnit.DAYS);
        Long activeUsersLast7d = (Long) entityManager.createNativeQuery(
                        "SELECT COUNT(DISTINCT user_id) FROM gps_points WHERE timestamp >= :threshold")
                .setParameter("threshold", last7d)
                .getSingleResult();
        usersActiveLast7d.set(activeUsersLast7d);

        // Count users with any GPS data
        Long usersWithData = (Long) entityManager.createNativeQuery(
                        "SELECT COUNT(DISTINCT user_id) FROM gps_points")
                .getSingleResult();
        usersWithGpsData.set(usersWithData);
    }
}
