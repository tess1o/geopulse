package org.github.tess1o.geopulse.geofencing.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleStateEntity;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleStateId;

@ApplicationScoped
public class GeofenceRuleStateRepository implements PanacheRepositoryBase<GeofenceRuleStateEntity, GeofenceRuleStateId> {
}
