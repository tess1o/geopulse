package org.github.tess1o.geopulse.notifications.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.enterprise.inject.Instance;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.notifications.model.dto.UnreadCountDto;
import org.github.tess1o.geopulse.notifications.model.dto.UserNotificationPageDto;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.dto.UserNotificationDto;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.notifications.model.entity.UserNotificationEntity;
import org.github.tess1o.geopulse.notifications.repository.UserNotificationRepository;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class UserNotificationService {

    private static final int MAX_LIMIT = 200;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserNotificationRepository notificationRepository;
    private final Map<NotificationSource, NotificationSeenSyncAdapter> syncAdaptersBySource;

    @Inject
    public UserNotificationService(UserNotificationRepository notificationRepository,
                                   Instance<NotificationSeenSyncAdapter> syncAdapters) {
        this.notificationRepository = notificationRepository;
        this.syncAdaptersBySource = new EnumMap<>(NotificationSource.class);
        for (NotificationSeenSyncAdapter adapter : syncAdapters) {
            this.syncAdaptersBySource.put(adapter.source(), adapter);
        }
    }

    public List<UserNotificationDto> listNotifications(UUID ownerUserId, int limit) {
        int normalizedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return notificationRepository.findByOwner(ownerUserId, normalizedLimit)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public UserNotificationPageDto listNotificationsPage(UUID ownerUserId,
                                                         int page,
                                                         int pageSize,
                                                         Boolean seen,
                                                         NotificationSource source,
                                                         NotificationType type) {
        int normalizedPage = Math.max(page, 0);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        UserNotificationRepository.UserNotificationPageResult result = notificationRepository.findPageByOwner(
                ownerUserId,
                normalizedPage,
                normalizedPageSize,
                seen,
                source,
                type
        );

        return UserNotificationPageDto.builder()
                .items(result.items().stream().map(this::toDto).toList())
                .totalCount(result.totalCount())
                .page(normalizedPage)
                .pageSize(normalizedPageSize)
                .build();
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

        Instant seenAt = entity.getSeenAt() == null ? Instant.now() : entity.getSeenAt();
        if (entity.getSeenAt() == null) {
            entity.setSeenAt(seenAt);
        }
        syncSeen(ownerUserId, entity, seenAt);

        return toDto(entity);
    }

    @Transactional
    public long markAllSeen(UUID ownerUserId) {
        Instant seenAt = Instant.now();
        long updatedCount = notificationRepository.markAllSeenByOwner(ownerUserId, seenAt);
        for (NotificationSeenSyncAdapter adapter : syncAdaptersBySource.values()) {
            adapter.markAllSeen(ownerUserId, seenAt);
        }
        return updatedCount;
    }

    private void syncSeen(UUID ownerUserId, UserNotificationEntity notification, Instant seenAt) {
        NotificationSeenSyncAdapter adapter = syncAdaptersBySource.get(notification.getSource());
        if (adapter != null) {
            adapter.markSeen(ownerUserId, notification, seenAt);
        }
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
