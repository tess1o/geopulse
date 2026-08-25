package org.github.tess1o.geopulse.geocoding.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.geocoding.model.CustomGeocodingProviderEntity;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class CustomGeocodingProviderRepository implements PanacheRepository<CustomGeocodingProviderEntity> {

    public Optional<CustomGeocodingProviderEntity> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return find("lower(name) = ?1", name.trim().toLowerCase()).firstResultOptional();
    }

    public boolean existsByName(String name) {
        return findByName(name).isPresent();
    }

    public List<CustomGeocodingProviderEntity> listEnabled() {
        return list("enabled = true order by displayName asc");
    }

    public List<CustomGeocodingProviderEntity> listAllSorted() {
        return list("order by displayName asc");
    }
}
