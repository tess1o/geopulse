package org.github.tess1o.geopulse.admin.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.admin.model.SystemSettingsEntity;

import java.util.List;
import java.util.Optional;

/**
 * Repository for system settings.
 */
@ApplicationScoped
public class SystemSettingsRepository implements PanacheRepositoryBase<SystemSettingsEntity, String> {

    public Optional<SystemSettingsEntity> findByKey(String key) {
        return find("key", key).firstResultOptional();
    }

    public List<SystemSettingsEntity> findByCategory(String category) {
        return list("category", category);
    }

    public void deleteByKey(String key) {
        delete("key", key);
    }

    public boolean existsByKey(String key) {
        return count("key", key) > 0;
    }
}
