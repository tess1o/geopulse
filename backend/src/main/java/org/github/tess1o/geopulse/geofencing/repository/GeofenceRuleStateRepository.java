package org.github.tess1o.geopulse.geofencing.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleStateEntity;

@ApplicationScoped
public class GeofenceRuleStateRepository implements PanacheRepository<GeofenceRuleStateEntity> {
}
