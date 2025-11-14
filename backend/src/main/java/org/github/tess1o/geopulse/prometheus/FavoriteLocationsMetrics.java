package org.github.tess1o.geopulse.prometheus;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class FavoriteLocationsMetrics {

    private final AtomicLong favoriteLocationsTotal = new AtomicLong();
    private final Map<String, AtomicLong> favoritePerUser = new ConcurrentHashMap<>();

    @Inject
    MeterRegistry registry;

    @Inject
    FavoritesRepository repository;

    @Inject
    UserRepository userRepository;

    void onStart(@Observes StartupEvent ev) {
        favoriteLocationsTotal.set(repository.count());
        Gauge.builder("favorite_locations_total", favoriteLocationsTotal, AtomicLong::get)
                .description("Total number of Favorite Locations")
                .register(registry);
    }

    @Scheduled(every = "10m")
    void refreshTotals() {
        favoriteLocationsTotal.set(repository.count());

        for (UserEntity user : userRepository.findAll().stream().toList()) {
            // GPS points per user
            AtomicLong countHolder = favoritePerUser.computeIfAbsent(user.getEmail(), e -> {
                AtomicLong h = new AtomicLong();
                Gauge.builder("favorite_locations_total", h, AtomicLong::get)
                        .tag("user", e)
                        .description("Total number of Favorite Locations for this user")
                        .register(registry);
                return h;
            });
            countHolder.set(repository.countByUserId(user.getId()));
        }
    }
}
