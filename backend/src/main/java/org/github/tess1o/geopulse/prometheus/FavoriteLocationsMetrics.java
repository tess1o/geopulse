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
import org.github.tess1o.geopulse.favorites.repository.FavoritesRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
@Slf4j
public class FavoriteLocationsMetrics {

    private final AtomicLong favoriteLocationsTotal = new AtomicLong();
    private final AtomicLong avgFavoritesPerUser = new AtomicLong();
    private final Map<String, AtomicLong> favoritePerUser = new ConcurrentHashMap<>();

    @Inject
    MeterRegistry registry;

    @Inject
    FavoritesRepository repository;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    void onStart(@Observes StartupEvent ev) {
        try {
            setMetricValues();

            Gauge.builder("favorite_locations_total", favoriteLocationsTotal, AtomicLong::get)
                    .description("Total number of Favorite Locations")
                    .register(registry);
            Gauge.builder("favorite_locations_avg_per_user", avgFavoritesPerUser, AtomicLong::get)
                    .description("Average number of Favorite Locations per user (among users with favorites)")
                    .register(registry);
        } catch (Exception e) {
            log.error("Failed to initialize Favorite Locations metrics", e);
        }
    }

    @Scheduled(every = "10m")
    void refreshTotals() {
        setMetricValues();
    }

    private void setMetricValues() {
        long total = repository.count();
        favoriteLocationsTotal.set(total);

        // Calculate average favorites per user (among users with favorites)
        Long usersWithFavorites = (Long) entityManager.createNativeQuery(
                "SELECT COUNT(DISTINCT user_id) FROM favorite_locations")
                .getSingleResult();
        if (usersWithFavorites > 0) {
            avgFavoritesPerUser.set(total / usersWithFavorites);
        } else {
            avgFavoritesPerUser.set(0);
        }

        for (UserEntity user : userRepository.findAll().stream().toList()) {
            // Favorite locations per user
            AtomicLong countHolder = favoritePerUser.computeIfAbsent(user.getEmail(), e -> {
                AtomicLong h = new AtomicLong();
                Gauge.builder("favorite_locations_per_user_total", h, AtomicLong::get)
                        .tag("user", e)
                        .description("Total number of Favorite Locations for this user")
                        .register(registry);
                return h;
            });
            countHolder.set(repository.countByUserId(user.getId()));
        }
    }
}
