package org.github.tess1o.geopulse.geofencing.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceDeliveryStatus;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class GeofenceEventRepository implements PanacheRepository<GeofenceEventEntity> {

    public List<GeofenceEventEntity> findByOwner(UUID ownerUserId, int limit, boolean unreadOnly) {
        String query = unreadOnly
                ? "ownerUser.id = ?1 AND seenAt IS NULL ORDER BY occurredAt DESC"
                : "ownerUser.id = ?1 ORDER BY occurredAt DESC";
        return find(query, ownerUserId)
                .page(0, Math.max(1, limit))
                .list();
    }

    public long countUnreadByOwner(UUID ownerUserId) {
        return count("ownerUser.id = ?1 AND seenAt IS NULL", ownerUserId);
    }

    public Optional<GeofenceEventEntity> findByIdAndOwner(Long eventId, UUID ownerUserId) {
        return find("id = ?1 AND ownerUser.id = ?2", eventId, ownerUserId).firstResultOptional();
    }

    public long markAllSeenByOwner(UUID ownerUserId, Instant seenAt) {
        return update("seenAt = ?1 WHERE ownerUser.id = ?2 AND seenAt IS NULL", seenAt, ownerUserId);
    }

    public List<GeofenceEventEntity> findPendingForDelivery(int limit, int maxAttempts) {
        return find("deliveryStatus = ?1 AND deliveryAttempts < ?2 ORDER BY createdAt ASC",
                GeofenceDeliveryStatus.PENDING,
                maxAttempts)
                .page(0, Math.max(1, limit))
                .list();
    }
}
