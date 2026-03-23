package org.github.tess1o.geopulse.geofencing.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class GeofenceRuleRepository implements PanacheRepository<GeofenceRuleEntity> {

    public List<GeofenceRuleEntity> findByOwner(UUID ownerUserId) {
        return list("ownerUser.id = ?1 ORDER BY updatedAt DESC", ownerUserId);
    }

    public Optional<GeofenceRuleEntity> findByIdAndOwner(Long id, UUID ownerUserId) {
        return find("id = ?1 AND ownerUser.id = ?2", id, ownerUserId).firstResultOptional();
    }

    public List<GeofenceRuleEntity> findActiveBySubject(UUID subjectUserId) {
        return list("SELECT DISTINCT rule " +
                "FROM GeofenceRuleEntity rule " +
                "JOIN rule.subjectAssignments assignment " +
                "WHERE assignment.subjectUser.id = ?1 AND rule.status = ?2", subjectUserId, GeofenceRuleStatus.ACTIVE);
    }
}
