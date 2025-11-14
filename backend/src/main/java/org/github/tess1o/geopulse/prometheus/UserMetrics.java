package org.github.tess1o.geopulse.prometheus;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class UserMetrics {

    private final AtomicLong usersTotal = new AtomicLong();

    @Inject
    MeterRegistry registry;

    @Inject
    UserRepository userRepository;

    void onStart(@Observes StartupEvent ev) {
        usersTotal.set(userRepository.count());
        Gauge.builder("users_total", usersTotal, AtomicLong::get)
                .description("Total number of users")
                .register(registry);
    }

    @Scheduled(every = "10m")
    void refreshTotals() {
        usersTotal.set(userRepository.count());
    }
}
