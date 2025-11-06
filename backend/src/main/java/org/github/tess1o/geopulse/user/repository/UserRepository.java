package org.github.tess1o.geopulse.user.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.model.UserSearchDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<UserEntity, UUID> {

    private final EntityManager entityManager;

    @Inject
    public UserRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
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
     * Search users to invite by excluding current user, existing friends, and users with pending invitations.
     * This is an optimized single SQL query version.
     *
     * @param currentUserId The ID of the current user
     * @param searchQuery The search query (email or full name)
     * @return List of UserSearchDTO matching the criteria
     */
    public List<UserSearchDTO> searchUsersToInvite(UUID currentUserId, String searchQuery) {
        String likeQuery = "%" + searchQuery.toLowerCase() + "%";

        String sql = """
                SELECT u.id, u.email, u.full_name, u.avatar
                FROM users u
                WHERE (LOWER(u.email) LIKE :searchQuery OR LOWER(u.full_name) LIKE :searchQuery)
                  AND u.id != :currentUserId
                  AND NOT EXISTS (
                      SELECT 1 FROM user_friends f
                      WHERE f.user_id = :currentUserId AND f.friend_id = u.id
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM friend_invitations fi
                      WHERE fi.status = 'PENDING'
                        AND ((fi.sender_id = :currentUserId AND fi.receiver_id = u.id)
                             OR (fi.sender_id = u.id AND fi.receiver_id = :currentUserId))
                  )
                LIMIT 20
                """;

        Query query = entityManager.createNativeQuery(sql)
                .setParameter("searchQuery", likeQuery)
                .setParameter("currentUserId", currentUserId);

        List<Object[]> results = query.getResultList();

        return results.stream()
                .map(record -> UserSearchDTO.builder()
                        .userId(UUID.fromString(record[0].toString()))
                        .email(record[1].toString())
                        .fullName(record[2].toString())
                        .avatar(record[3] == null ? "" : record[3].toString())
                        .build())
                .toList();
    }
}
