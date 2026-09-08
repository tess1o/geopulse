package org.github.tess1o.geopulse.notifications.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.notifications.model.NotificationPreferences;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.Locale;

@ApplicationScoped
@Slf4j
public class RewindNotificationService {
    private final UserRepository userRepository;
    private final GpsPointRepository pointRepository;
    private final NotificationPreferencesService preferencesService;
    private final NotificationPublisherService publisher;

    public RewindNotificationService(UserRepository userRepository, GpsPointRepository pointRepository,
                                     NotificationPreferencesService preferencesService, NotificationPublisherService publisher) {
        this.userRepository = userRepository;
        this.pointRepository = pointRepository;
        this.preferencesService = preferencesService;
        this.publisher = publisher;
    }

    @Scheduled(every = "15m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP, identity = "rewind-notification")
    @Transactional
    public void notifyReady() {
        Instant now = Instant.now();
        for (UserEntity user : userRepository.findActiveUsers()) {
            try {
                NotificationPreferences preferences = preferencesService.getEntityPreferences(user);
                if (!preferences.isRewindEnabled()) continue;
                ZoneId zone = ZoneId.of(user.getTimezone());
                ZonedDateTime localNow = now.atZone(zone);
                if (localNow.getDayOfMonth() != 1 || localNow.getHour() != 9) continue;
                YearMonth period = YearMonth.from(localNow).minusMonths(1);
                Instant start = period.atDay(1).atStartOfDay(zone).toInstant();
                Instant end = period.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
                if (!pointRepository.existsByUserIdAndTimePeriod(user.getId(), start, end)) continue;
                String route = "/app/rewind?viewMode=monthly&year=" + period.getYear() + "&month=" + period.getMonthValue();
                var metadata = new LinkedHashMap<String, Object>();
                metadata.put("rewindPeriod", period.toString());
                metadata.put("targetRoute", route);
                log.info("Publishing Rewind notification for user {} and period {}", user.getId(), period);
                publisher.publish(user, NotificationSource.REWIND, NotificationType.REWIND_READY,
                        "Your " + period.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault()) + " Rewind is ready", "Your completed monthly location story is ready to explore.",
                        metadata, "rewind:" + user.getId() + ":" + period, preferences.getRewind());
            } catch (Exception exception) {
                log.warn("Rewind notification check failed for user {}: {}", user.getId(), exception.getMessage());
            }
        }
    }
}
