package org.github.tess1o.geopulse.notifications.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.notifications.model.dto.UnreadCountDto;
import org.github.tess1o.geopulse.notifications.model.dto.UserNotificationDto;
import org.github.tess1o.geopulse.notifications.model.entity.UserNotificationEntity;
import org.github.tess1o.geopulse.notifications.repository.UserNotificationRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class UserNotificationService {

    private static final int MAX_LIMIT = 200;

    private final UserNotificationRepository notificationRepository;

    @Inject
    public UserNotificationService(UserNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<UserNotificationDto> listNotifications(UUID ownerUserId, int limit) {
        int normalizedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return notificationRepository.findByOwner(ownerUserId, normalizedLimit)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public UnreadCountDto getUnreadCount(UUID ownerUserId) {
        long count = notificationRepository.countUnreadByOwner(ownerUserId);
        Long latestUnreadId = count > 0 ? notificationRepository.findLatestUnreadIdByOwner(ownerUserId) : null;
        return new UnreadCountDto(count, latestUnreadId);
    }

    @Transactional
    public UserNotificationDto markSeen(UUID ownerUserId, Long notificationId) {
        UserNotificationEntity entity = notificationRepository.findByIdAndOwner(notificationId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

        if (entity.getSeenAt() == null) {
            entity.setSeenAt(Instant.now());
        }

        return toDto(entity);
    }

    @Transactional
    public long markAllSeen(UUID ownerUserId) {
        return notificationRepository.markAllSeenByOwner(ownerUserId, Instant.now());
    }

    public UserNotificationDto toDto(UserNotificationEntity entity) {
        return UserNotificationDto.builder()
                .id(entity.getId())
                .source(entity.getSource())
                .type(entity.getType())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .occurredAt(entity.getOccurredAt())
                .seenAt(entity.getSeenAt())
                .seen(entity.getSeenAt() != null)
                .deliveryStatus(entity.getDeliveryStatus())
                .objectRef(entity.getObjectRef())
                .metadata(entity.getMetadata())
                .build();
    }
}
