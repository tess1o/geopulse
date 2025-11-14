package org.github.tess1o.geopulse.prometheus;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.github.tess1o.geopulse.streaming.repository.TimelineDataGapRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineStayRepository;
import org.github.tess1o.geopulse.streaming.repository.TimelineTripRepository;
import org.github.tess1o.geopulse.streaming.service.StreamingTimelineAggregator;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class TimelineMetrics {

    private final AtomicLong staysTotal = new AtomicLong();
    private final AtomicLong tripsTotal = new AtomicLong();
    private final AtomicLong dataGapsTotal = new AtomicLong();
    private final Map<String, AtomicLong> staysPerUser = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> tripsPerUser = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> dataGapsPerUser = new ConcurrentHashMap<>();

    @Inject
    MeterRegistry registry;

    @Inject
    TimelineStayRepository stayRepository;

    @Inject
    TimelineTripRepository tripRepository;

    @Inject
    TimelineDataGapRepository dataGapRepository;

    @Inject
    UserRepository userRepository;

    void onStart(@Observes StartupEvent ev) {
        setMetrics();
        Gauge.builder("timeline_stays_total", staysTotal, AtomicLong::get)
                .register(registry);
        Gauge.builder("timeline_trips_total", tripsTotal, AtomicLong::get)
                .register(registry);
        Gauge.builder("timeline_data_gaps_total", dataGapsTotal, AtomicLong::get)
                .register(registry);
    }

    @Scheduled(every = "10m")
    void refreshTotals() {
        setMetrics();
    }

    private void setMetrics() {
        staysTotal.set(stayRepository.count());
        tripsTotal.set(tripRepository.count());
        dataGapsTotal.set(dataGapRepository.count());

        for (UserEntity user : userRepository.findAll().stream().toList()) {
            AtomicLong stayCountHolder = staysPerUser.computeIfAbsent(user.getEmail(), e -> {
                AtomicLong h = new AtomicLong();
                Gauge.builder("timeline_stays_total", h, AtomicLong::get)
                        .tag("user", e)
                        .description("Total number of Timeline stays for this user")
                        .register(registry);
                return h;
            });
            stayCountHolder.set(stayRepository.countByUser(user.getId()));

            AtomicLong tripCountHolder = tripsPerUser.computeIfAbsent(user.getEmail(), e -> {
                AtomicLong h = new AtomicLong();
                Gauge.builder("timeline_trips_total", h, AtomicLong::get)
                        .tag("user", e)
                        .description("Total number of Timeline trips for this user")
                        .register(registry);
                return h;
            });
            tripCountHolder.set(tripRepository.countByUser(user.getId()));

            AtomicLong dataGapsCountHolder = dataGapsPerUser.computeIfAbsent(user.getEmail(), e -> {
                AtomicLong h = new AtomicLong();
                Gauge.builder("timeline_data_gaps_total", h, AtomicLong::get)
                        .tag("user", e)
                        .description("Total number of Timeline data gaps for this user")
                        .register(registry);
                return h;
            });
            dataGapsCountHolder.set(dataGapRepository.countByUser(user.getId()));
        }
    }
}
