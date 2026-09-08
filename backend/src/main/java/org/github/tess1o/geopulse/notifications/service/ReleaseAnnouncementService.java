package org.github.tess1o.geopulse.notifications.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.home.model.HomeContentResponse;
import org.github.tess1o.geopulse.home.service.HomeContentService;
import org.github.tess1o.geopulse.notifications.model.dto.ReleaseAnnouncementResponse;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.notifications.model.entity.UserNotificationEntity;
import org.github.tess1o.geopulse.notifications.repository.UserNotificationRepository;
import org.github.tess1o.geopulse.notifications.model.NotificationPreferences;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.LinkedHashMap;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class ReleaseAnnouncementService {
    private final UserRepository userRepository;
    private final UserNotificationRepository notificationRepository;
    private final NotificationPublisherService publisher;
    private final UserNotificationService notificationService;
    private final HomeContentService homeContentService;
    private final String version;

    public ReleaseAnnouncementService(UserRepository userRepository, UserNotificationRepository notificationRepository,
                                      NotificationPublisherService publisher, UserNotificationService notificationService,
                                      HomeContentService homeContentService,
                                      @ConfigProperty(name = "quarkus.application.version") String version) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.publisher = publisher;
        this.notificationService = notificationService;
        this.homeContentService = homeContentService;
        this.version = version;
    }

    @Transactional
    public ReleaseAnnouncementResponse current(UUID userId) {
        HomeContentResponse.WhatsNewItem release = homeContentService.getContent().whatsNew().stream()
                .filter(item -> version.equals(item.version())).findFirst().orElse(null);
        if (release == null) return new ReleaseAnnouncementResponse(false, null, null);
        UserEntity user = userRepository.findByIdOptional(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        NotificationPreferences preferences = user.getNotificationPreferences();
        if (preferences != null && Boolean.FALSE.equals(preferences.getWhatsNewEnabled())) {
            log.debug("Release announcement is disabled for user {}", userId);
            return new ReleaseAnnouncementResponse(false, null, null);
        }
        String dedupeKey = "product-release:" + userId + ":" + version;
        UserNotificationEntity notification = notificationRepository.findByDedupeKey(dedupeKey).orElseGet(() -> {
            log.info("Publishing release announcement for user {} and version {}", userId, version);
            var metadata = new LinkedHashMap<String, Object>();
            metadata.put("releaseVersion", release.version());
            metadata.put("highlights", release.highlights());
            metadata.put("releaseUrl", release.releaseUrl());
            metadata.put("targetRoute", "/app/notifications");
            return publisher.publish(user, NotificationSource.PRODUCT, NotificationType.PRODUCT_RELEASE_AVAILABLE,
                    release.title(), String.join(" • ", release.highlights()), metadata, dedupeKey, null);
        });
        return new ReleaseAnnouncementResponse(notification.getSeenAt() == null, notificationService.toDto(notification), release);
    }
}
