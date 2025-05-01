package org.github.tess1o.geopulse.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.model.entity.UserEntity;

import java.util.Optional;

@ApplicationScoped
public class UserRepository implements PanacheRepository<UserEntity> {
    /**
     * Find a single user by userId
     */
    public Optional<UserEntity> findByUserId(String userId) {
        return find("userId", userId).firstResultOptional();
    }

    /**
     * Check if a user with the given userId exists
     */
    public boolean existsByUserId(String userId) {
        return findByUserId(userId).isPresent();
    }
}
