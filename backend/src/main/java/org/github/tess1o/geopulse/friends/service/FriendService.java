package org.github.tess1o.geopulse.friends.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.friends.exceptions.FriendsException;
import org.github.tess1o.geopulse.friends.model.FriendInfoDTO;
import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.service.LocationPointResolver;
import org.github.tess1o.geopulse.user.exceptions.NotAuthorizedUserException;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Slf4j
public class FriendService {

    private final GpsPointRepository gpsPointRepository;
    private final LocationPointResolver locationPointResolver;
    private final FriendshipRepository friendshipRepository;

    @Inject
    public FriendService(
            GpsPointRepository gpsPointRepository,
            LocationPointResolver locationPointResolver,
            FriendshipRepository friendshipRepository) {
        this.gpsPointRepository = gpsPointRepository;
        this.locationPointResolver = locationPointResolver;
        this.friendshipRepository = friendshipRepository;
    }

    /**
     * Remove a friend.
     *
     * @param userId   The ID of the user removing the friend
     * @param friendId The ID of the friend to remove
     * @throws FriendsException if the friend cannot be removed
     */
    @Transactional
    public void removeFriend(UUID userId, UUID friendId) {
        // Check if they are friends
        if (!friendshipRepository.existsFriendship(userId, friendId)) {
            throw new FriendsException("Not friends with user: " + friendId);
        }

        // Delete the friendship (both directions)
        long deletedCount = friendshipRepository.deleteFriendship(userId, friendId);
        log.info("Friendship removed between {} and {}, deleted {} records", userId, friendId, deletedCount);
    }

    /**
     * Get the most recent location of a friend.
     *
     * @param userId   The ID of the user requesting the location
     * @param friendId The ID of the friend
     * @return The most recent location entity of the friend
     * @throws NotAuthorizedUserException if the location cannot be retrieved
     */
    public GpsPointEntity getFriendLocation(UUID userId, UUID friendId) {
        // Check if they are friends
        if (!friendshipRepository.existsFriendship(userId, friendId)) {
            throw new NotAuthorizedUserException("Not authorized to view location of user: " + friendId);
        }
        return gpsPointRepository.findByUserIdLatestGpsPoint(friendId);
    }


    public List<FriendInfoDTO> getAllFriends(UUID userId) {
        List<FriendInfoDTO> friends = friendshipRepository.findFriends(userId);

        friends.forEach(f -> {
            if (f.getLastLongitude() == null || f.getLastLatitude() == null) {
                f.setLastLocation("N/A");
            } else {
                Point lastLocation = GeoUtils.createPoint(f.getLastLongitude(), f.getLastLatitude());
                f.setLastLocation(locationPointResolver.resolveLocationWithReferences(f.getUserId(), lastLocation).getLocationName());
            }
        });
        return friends;
    }
}
