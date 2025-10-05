package org.github.tess1o.geopulse.friends.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.geolatte.geom.Point;
import org.github.tess1o.geopulse.friends.model.FriendInfoDTO;
import org.github.tess1o.geopulse.friends.model.UserFriendEntity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class FriendshipRepository implements PanacheRepository<UserFriendEntity> {

    private final EntityManager entityManager;

    @Inject
    public FriendshipRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Check if a friendship exists between two users.
     *
     * @param userId   The ID of the first user
     * @param friendId The ID of the second user
     * @return true if a friendship exists, false otherwise
     */
    public boolean existsFriendship(UUID userId, UUID friendId) {
        return count("user.id = ?1 AND friend.id = ?2", userId, friendId) > 0;
    }

    /**
     * Delete a friendship between two users.
     * This deletes both directions of the friendship (A->B and B->A).
     *
     * @param userId   The ID of the first user
     * @param friendId The ID of the second user
     * @return The number of deleted entities
     */
    public long deleteFriendship(UUID userId, UUID friendId) {
        // Delete both directions of the friendship
        long count1 = delete("user.id = ?1 AND friend.id = ?2", userId, friendId);
        long count2 = delete("user.id = ?1 AND friend.id = ?2", friendId, userId);
        return count1 + count2;
    }

    public List<FriendInfoDTO> findFriends(UUID userId) {
        String sql = """
                select user_id, friend_id, email, full_name, avatar, t.timestamp, coordinates
                from (select f.user_id,
                             f.friend_id,
                             u.email,
                             u.full_name,
                             u.avatar,
                             gps.timestamp,
                             gps.coordinates,
                             rank() over (partition by f.friend_id order by gps.timestamp desc) as r
                      from user_friends f
                               join users u on f.friend_id = u.id
                               left join gps_points gps on u.id = gps.user_id
                      where f.user_id = :userId) t
                where r = 1;
                """;

        // Create a native query and set the parameter
        Query query = entityManager.createNativeQuery(sql)
                .setParameter("userId", userId);

        // Execute the query and transform the results to DTOs
        List<Object[]> results = query.getResultList();

        // Map the results to the DTO
        return results.stream()
                .map(record -> FriendInfoDTO.builder()
                        .userId(UUID.fromString(record[0].toString()))
                        .friendId(UUID.fromString(record[1].toString()))
                        .email(record[2].toString())
                        .fullName(record[3].toString())
                        .avatar(record[4] == null ? "" : record[4].toString())
                        .lastSeen(getLastSeen(record[5]))
                        .lastLongitude(getCoordinate(record[6], 0))
                        .lastLatitude(getCoordinate(record[6], 1))
                        .build())
                .toList();
    }

    private static String getLastSeen(Object value) {
        if (value == null) {
            return null;
        }
        // Convert timestamp correctly - database stores UTC timestamps
        // Using toLocalDateTime().toInstant(ZoneOffset.UTC) to avoid timezone conversion
        LocalDateTime timestamp = (LocalDateTime) value;
        return timestamp.toInstant(ZoneOffset.UTC).toString();
    }

    private static Double getCoordinate(Object value, int index) {
        if (value == null) {
            return null;
        }
        return ((Point) value).getPosition().getCoordinate(index);
    }
}
