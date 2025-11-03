package org.github.tess1o.geopulse.user.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.user.model.UserEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<UserEntity, UUID> {
    /**
     * Find a single user by email
     */

    public Optional<UserEntity> findByEmail(String email) {
        return find("email = ?1", email).firstResultOptional();
    }

    /**
     * Check if a user with the given userId exists
     */
    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    public boolean existsById(UUID userId) {
        return findById(userId) != null;
    }

    /**
     * Find all active users (not deleted/deactivated).
     * Used for batch processing operations like daily timeline generation.
     */
    public List<UserEntity> findActiveUsers() {
        return list("isActive = true");
    }

    public List<UserEntity> findByEmailOrFullNameContainingIgnoreCase(String query) {
        String likeQuery = "%" + query.toLowerCase() + "%";
        return list("lower(email) LIKE ?1 OR lower(fullName) LIKE ?1", likeQuery);
    }
}
