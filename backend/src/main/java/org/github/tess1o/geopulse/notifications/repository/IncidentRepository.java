package org.github.tess1o.geopulse.notifications.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.notifications.model.entity.IncidentEntity;

@ApplicationScoped
public class IncidentRepository implements PanacheRepositoryBase<IncidentEntity, String> {
}
