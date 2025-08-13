package org.github.tess1o.geopulse.gpssource.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class GpsSourceRepository implements PanacheRepositoryBase<GpsSourceConfigEntity, UUID> {

    public List<GpsSourceConfigEntity> findByUserId(UUID userId) {
        return list("user.id = ?1", userId);
    }

    public Optional<GpsSourceConfigEntity> findByConfigIdAndUserId(UUID configId, UUID userId) {
        return list("user.id = ?1 and id = ?2", userId, configId).stream().findFirst();
    }

    public List<GpsSourceConfigEntity> findByUserIdAndSourceType(UUID userId, GpsSourceType sourceType) {
        return list("user.id = ?1 and sourceType = ?2", userId, sourceType);
    }

    public long deleteByUserIdAndConfigId(UUID configId, UUID userId) {
        return delete("id = ?1 and user.id = ?2", configId, userId);
    }

    public Optional<GpsSourceConfigEntity> findByUsername(String username) {
        return find("username = ?1", username).firstResultOptional();
    }

    public Optional<GpsSourceConfigEntity> findByToken(String username) {
        return find("token = ?1", username).firstResultOptional();
    }

    public Optional<GpsSourceConfigEntity> findByUsernameAndConnectionType(String username, GpsSourceConfigEntity.ConnectionType connectionType) {
        return find("username = ?1 and connectionType = ?2 and active = true", username, connectionType).firstResultOptional();
    }

    public boolean existsByUserAndUsername(UUID userId, String username) {
        return count("user.id = ?1 AND username = ?2", userId, username) > 0;
    }

    public long deleteByUserId(UUID userId) {
        return delete("user.id = ?1", userId);
    }
}
