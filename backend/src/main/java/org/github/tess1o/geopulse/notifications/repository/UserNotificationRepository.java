package org.github.tess1o.geopulse.notifications.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.UserNotificationEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserNotificationRepository implements PanacheRepository<UserNotificationEntity> {

    public List<UserNotificationEntity> findByOwner(UUID ownerUserId, int limit) {
        return find("ownerUser.id = ?1 ORDER BY occurredAt DESC", ownerUserId)
                .page(0, Math.max(1, limit))
                .list();
    }

    public long countUnreadByOwner(UUID ownerUserId) {
        return count("ownerUser.id = ?1 AND seenAt IS NULL", ownerUserId);
    }

    public Long findLatestUnreadIdByOwner(UUID ownerUserId) {
        return find("ownerUser.id = ?1 AND seenAt IS NULL ORDER BY id DESC", ownerUserId)
                .firstResultOptional()
                .map(UserNotificationEntity::getId)
                .orElse(null);
    }

    public Optional<UserNotificationEntity> findByIdAndOwner(Long notificationId, UUID ownerUserId) {
        return find("id = ?1 AND ownerUser.id = ?2", notificationId, ownerUserId).firstResultOptional();
    }

    public long markAllSeenByOwner(UUID ownerUserId, Instant seenAt) {
        return update("seenAt = ?1 WHERE ownerUser.id = ?2 AND seenAt IS NULL", seenAt, ownerUserId);
    }

    public Optional<UserNotificationEntity> findByDedupeKey(String dedupeKey) {
        return find("dedupeKey", dedupeKey).firstResultOptional();
    }

    public Optional<UserNotificationEntity> findBySourceAndObjectRefAndOwner(NotificationSource source,
                                                                              String objectRef,
                                                                              UUID ownerUserId) {
        return find("source = ?1 AND objectRef = ?2 AND ownerUser.id = ?3", source, objectRef, ownerUserId)
                .firstResultOptional();
    }

    public long deleteOlderThan(Instant cutoff) {
        return delete("occurredAt < ?1", cutoff);
    }
}
