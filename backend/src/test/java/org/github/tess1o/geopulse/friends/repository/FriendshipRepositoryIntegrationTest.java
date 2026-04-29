package org.github.tess1o.geopulse.friends.repository;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.friends.model.FriendInfoDTO;
import org.github.tess1o.geopulse.friends.model.UserFriendEntity;
import org.github.tess1o.geopulse.friends.model.UserFriendPermissionEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class FriendshipRepositoryIntegrationTest {

    @Inject
    FriendshipRepository friendshipRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    private UserEntity ownerUser;
    private UserEntity friendUser;

    @BeforeEach
    @Transactional
    void setUp() {
        ownerUser = createUser("owner");
        friendUser = createUser("friend");
        createFriendship(ownerUser, friendUser);
        createPermission(friendUser, ownerUser, true, true);
        createGpsPoint(friendUser, Instant.parse("2026-03-06T17:05:34Z"), 81.0);
        entityManager.flush();
    }


    @Test
    @Transactional
    void findFriendsShouldReturnCorrectUtcLastSeenFromNativeQuery() {
        List<FriendInfoDTO> friends = friendshipRepository.findFriends(ownerUser.getId());
        assertEquals(1, friends.size());
        FriendInfoDTO friend = friends.get(0);
        assertNotNull(friend);
        assertEquals("2026-03-06T17:05:34Z", friend.getLastSeen());
        assertEquals(81.0, friend.getLastBattery());
    }

    @Test
    @Transactional
    void findFriendsShouldHideBatteryWhenLiveLocationPermissionDisabled() {
        entityManager.createNativeQuery("""
                UPDATE user_friend_permissions
                SET share_live_location = false
                WHERE user_id = :userId AND friend_id = :friendId
                """)
                .setParameter("userId", friendUser.getId())
                .setParameter("friendId", ownerUser.getId())
                .executeUpdate();

        List<FriendInfoDTO> friends = friendshipRepository.findFriends(ownerUser.getId());
        assertEquals(1, friends.size());
        FriendInfoDTO friend = friends.get(0);
        assertNotNull(friend);
        assertNull(friend.getLastBattery());
    }

    private UserEntity createUser(String prefix) {
        UserEntity user = new UserEntity();
        user.setEmail(prefix + "-" + System.nanoTime() + "@geopulse.app");
        user.setFullName(prefix + " user");
        user.setPasswordHash("test-hash");
        user.setTimezone("Europe/Kyiv");
        userRepository.persist(user);
        return user;
    }

    private void createFriendship(UserEntity user, UserEntity friend) {
        UserFriendEntity relationship = new UserFriendEntity();
        relationship.setUser(user);
        relationship.setFriend(friend);
        entityManager.persist(relationship);
    }

    private void createPermission(UserEntity owner, UserEntity friend, boolean shareLiveLocation, boolean shareTimeline) {
        UserFriendPermissionEntity permission = new UserFriendPermissionEntity();
        permission.setUser(owner);
        permission.setFriend(friend);
        permission.setShareLiveLocation(shareLiveLocation);
        permission.setShareTimeline(shareTimeline);
        entityManager.persist(permission);
    }
    private void createGpsPoint(UserEntity user, Instant timestamp, Double battery) {
        GpsPointEntity gpsPoint = new GpsPointEntity();
        gpsPoint.setUser(user);
        gpsPoint.setTimestamp(timestamp);
        gpsPoint.setCoordinates(GeoUtils.createPoint(25.595304, 49.550959));
        gpsPoint.setBattery(battery);
        gpsPoint.setSourceType(GpsSourceType.OWNTRACKS);
        gpsPoint.setCreatedAt(Instant.now());
        entityManager.persist(gpsPoint);
    }
}
