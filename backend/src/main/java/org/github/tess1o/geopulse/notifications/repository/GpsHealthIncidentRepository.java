package org.github.tess1o.geopulse.notifications.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.notifications.model.entity.GpsHealthIncidentEntity;

import java.util.UUID;

@ApplicationScoped
public class GpsHealthIncidentRepository implements PanacheRepositoryBase<GpsHealthIncidentEntity, UUID> {
}
