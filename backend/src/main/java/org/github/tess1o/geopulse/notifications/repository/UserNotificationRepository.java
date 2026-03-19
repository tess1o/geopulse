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

    public List<UserNotificationEntity> findByOwner(UUID ownerUserId,
                                                    int limit,
                                                    boolean unreadOnly,
                                                    NotificationSource source) {
        StringBuilder query = new StringBuilder("ownerUser.id = ?1");

        if (unreadOnly) {
            query.append(" AND seenAt IS NULL");
        }

        if (source != null) {
            query.append(" AND source = ?2 ORDER BY occurredAt DESC");
            return find(query.toString(), ownerUserId, source)
                    .page(0, Math.max(1, limit))
                    .list();
        }

        query.append(" ORDER BY occurredAt DESC");
        return find(query.toString(), ownerUserId)
                .page(0, Math.max(1, limit))
                .list();
    }

    public long countUnreadByOwner(UUID ownerUserId, NotificationSource source) {
        if (source == null) {
            return count("ownerUser.id = ?1 AND seenAt IS NULL", ownerUserId);
        }
        return count("ownerUser.id = ?1 AND seenAt IS NULL AND source = ?2", ownerUserId, source);
    }

    public Long findLatestUnreadIdByOwner(UUID ownerUserId, NotificationSource source) {
        if (source == null) {
            return find("ownerUser.id = ?1 AND seenAt IS NULL ORDER BY id DESC", ownerUserId)
                    .firstResultOptional()
                    .map(UserNotificationEntity::getId)
                    .orElse(null);
        }

        return find("ownerUser.id = ?1 AND seenAt IS NULL AND source = ?2 ORDER BY id DESC", ownerUserId, source)
                .firstResultOptional()
                .map(UserNotificationEntity::getId)
                .orElse(null);
    }

    public Optional<UserNotificationEntity> findByIdAndOwner(Long notificationId, UUID ownerUserId) {
        return find("id = ?1 AND ownerUser.id = ?2", notificationId, ownerUserId).firstResultOptional();
    }

    public long markAllSeenByOwner(UUID ownerUserId, Instant seenAt, NotificationSource source) {
        if (source == null) {
            return update("seenAt = ?1 WHERE ownerUser.id = ?2 AND seenAt IS NULL", seenAt, ownerUserId);
        }

        return update("seenAt = ?1 WHERE ownerUser.id = ?2 AND source = ?3 AND seenAt IS NULL", seenAt, ownerUserId, source);
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
