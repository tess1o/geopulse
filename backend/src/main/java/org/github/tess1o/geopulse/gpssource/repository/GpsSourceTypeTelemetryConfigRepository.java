package org.github.tess1o.geopulse.gpssource.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceTypeTelemetryConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class GpsSourceTypeTelemetryConfigRepository
        implements PanacheRepositoryBase<GpsSourceTypeTelemetryConfigEntity, UUID> {

    public Optional<GpsSourceTypeTelemetryConfigEntity> findByUserIdAndSourceType(UUID userId, GpsSourceType sourceType) {
        return find("user.id = ?1 and sourceType = ?2", userId, sourceType).firstResultOptional();
    }

    public List<GpsSourceTypeTelemetryConfigEntity> findByUserIdAndSourceTypes(UUID userId, Collection<GpsSourceType> sourceTypes) {
        if (sourceTypes == null || sourceTypes.isEmpty()) {
            return List.of();
        }
        return list("user.id = ?1 and sourceType in ?2", userId, sourceTypes);
    }

    public long deleteByUserIdAndSourceType(UUID userId, GpsSourceType sourceType) {
        return delete("user.id = ?1 and sourceType = ?2", userId, sourceType);
    }
}
