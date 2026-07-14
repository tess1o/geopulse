package org.github.tess1o.geopulse.notifications.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationSource;
import org.github.tess1o.geopulse.notifications.model.entity.NotificationType;
import org.github.tess1o.geopulse.notifications.model.entity.UserNotificationEntity;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserNotificationRepository implements PanacheRepository<UserNotificationEntity> {

    public record UserNotificationPageResult(List<UserNotificationEntity> items, long totalCount) {
    }

    public List<UserNotificationEntity> findByOwner(UUID ownerUserId, int limit) {
        return find("ownerUser.id = ?1 ORDER BY occurredAt DESC", ownerUserId)
                .page(0, Math.max(1, limit))
                .list();
    }

    public UserNotificationPageResult findPageByOwner(UUID ownerUserId,
                                                      int page,
                                                      int pageSize,
                                                      Boolean seen,
                                                      NotificationSource source,
                                                      NotificationType type) {
        StringBuilder where = new StringBuilder("n.ownerUser.id = :ownerUserId");
        Map<String, Object> params = new HashMap<>();
        params.put("ownerUserId", ownerUserId);

        if (seen != null) {
            where.append(seen ? " AND n.seenAt IS NOT NULL" : " AND n.seenAt IS NULL");
        }

        if (source != null) {
            where.append(" AND n.source = :source");
            params.put("source", source);
        }

        if (type != null) {
            where.append(" AND n.type = :type");
            params.put("type", type);
        }

        String select = "SELECT n FROM UserNotificationEntity n WHERE " + where + " ORDER BY n.occurredAt DESC, n.id DESC";
        TypedQuery<UserNotificationEntity> dataQuery = getEntityManager().createQuery(select, UserNotificationEntity.class);
        params.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult(Math.max(0, page) * Math.max(1, pageSize));
        dataQuery.setMaxResults(Math.max(1, pageSize));

        String countSql = "SELECT COUNT(n.id) FROM UserNotificationEntity n WHERE " + where;
        TypedQuery<Long> countQuery = getEntityManager().createQuery(countSql, Long.class);
        params.forEach(countQuery::setParameter);

        return new UserNotificationPageResult(dataQuery.getResultList(), countQuery.getSingleResult());
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

    public long markSeenBySourceAndObjectRefAndOwner(NotificationSource source,
                                                      String objectRef,
                                                      UUID ownerUserId,
                                                      Instant seenAt) {
        return update("seenAt = ?1 WHERE source = ?2 AND objectRef = ?3 AND ownerUser.id = ?4 AND seenAt IS NULL",
                seenAt,
                source,
                objectRef,
                ownerUserId);
    }

    public long markAllSeenBySourceAndOwner(NotificationSource source, UUID ownerUserId, Instant seenAt) {
        return update("seenAt = ?1 WHERE source = ?2 AND ownerUser.id = ?3 AND seenAt IS NULL",
                seenAt,
                source,
                ownerUserId);
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
