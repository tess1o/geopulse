package org.github.tess1o.geopulse.geofencing.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.geofencing.model.entity.NotificationTemplateEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class NotificationTemplateRepository implements PanacheRepository<NotificationTemplateEntity> {

    public List<NotificationTemplateEntity> findByUser(UUID userId) {
        return list("user.id = ?1 ORDER BY updatedAt DESC", userId);
    }

    public Optional<NotificationTemplateEntity> findByIdAndUser(Long id, UUID userId) {
        return find("id = ?1 AND user.id = ?2", id, userId).firstResultOptional();
    }

    public Optional<NotificationTemplateEntity> findDefaultEnterByUser(UUID userId) {
        return find("user.id = ?1 AND defaultForEnter = true AND enabled = true ORDER BY updatedAt DESC", userId)
                .firstResultOptional();
    }

    public Optional<NotificationTemplateEntity> findDefaultLeaveByUser(UUID userId) {
        return find("user.id = ?1 AND defaultForLeave = true AND enabled = true ORDER BY updatedAt DESC", userId)
                .firstResultOptional();
    }

    public boolean existsDefaultEnterByUser(UUID userId) {
        return count("user.id = ?1 AND defaultForEnter = true", userId) > 0;
    }

    public boolean existsDefaultLeaveByUser(UUID userId) {
        return count("user.id = ?1 AND defaultForLeave = true", userId) > 0;
    }

    public List<NotificationTemplateEntity> findDefaultEnterOthers(UUID userId, Long excludedId) {
        return list("user.id = ?1 AND defaultForEnter = true AND id <> ?2", userId, excludedId == null ? -1L : excludedId);
    }

    public List<NotificationTemplateEntity> findDefaultLeaveOthers(UUID userId, Long excludedId) {
        return list("user.id = ?1 AND defaultForLeave = true AND id <> ?2", userId, excludedId == null ? -1L : excludedId);
    }
}
