package org.github.tess1o.geopulse.admin.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.admin.model.OidcProviderEntity;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class OidcProviderRepository implements PanacheRepository<OidcProviderEntity> {

    public Optional<OidcProviderEntity> findByName(String name) {
        return find("name", name).firstResultOptional();
    }

    public List<OidcProviderEntity> findByEnabled(boolean enabled) {
        return find("enabled", enabled).list();
    }

    public boolean existsByName(String name) {
        return count("name", name) > 0;
    }

    public void deleteByName(String name) {
        delete("name", name);
    }
}
