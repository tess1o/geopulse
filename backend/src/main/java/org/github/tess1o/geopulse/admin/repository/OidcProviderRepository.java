package org.github.tess1o.geopulse.admin.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.admin.model.OidcProviderEntity;

import java.util.Optional;

@ApplicationScoped
public class OidcProviderRepository implements PanacheRepositoryBase<OidcProviderEntity, String> {

    @Inject
    EntityManager entityManager;

    public Optional<OidcProviderEntity> findByName(String name) {
        return find("name", name).firstResultOptional();
    }

    public boolean existsByName(String name) {
        return count("name", name) > 0;
    }

    public void deleteByName(String name) {
        findByName(name).ifPresent(entity -> {
            delete(entity);
            flush();
            entityManager.clear();
        });
    }
}
