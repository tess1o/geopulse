package org.github.tess1o.geopulse.geofencing.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleSubjectEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleSubjectId;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleStateEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleStateId;

import java.util.UUID;

@ApplicationScoped
public class GeofenceRuleStateRepository implements PanacheRepositoryBase<GeofenceRuleStateEntity, GeofenceRuleStateId> {

    public boolean lockSubjectAssignment(Long ruleId, UUID subjectUserId) {
        if (ruleId == null || subjectUserId == null) {
            return false;
        }

        GeofenceRuleSubjectId subjectId = new GeofenceRuleSubjectId(ruleId, subjectUserId);
        GeofenceRuleSubjectEntity assignment = getEntityManager().find(
                GeofenceRuleSubjectEntity.class,
                subjectId,
                LockModeType.PESSIMISTIC_WRITE
        );
        return assignment != null;
    }
}
