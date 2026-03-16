package org.github.tess1o.geopulse.ai.service;

import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.friends.model.FriendInfoDTO;
import org.github.tess1o.geopulse.friends.service.FriendService;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@Tag("unit")
class AIFriendLiveToolsTest {

    private static final UUID CURRENT_USER_ID = UUID.randomUUID();

    private CurrentUserService currentUserService;
    private FriendService friendService;
    private AIFriendLiveTools friendLiveTools;

    @BeforeEach
    void setUp() {
        currentUserService = Mockito.mock(CurrentUserService.class);
        friendService = Mockito.mock(FriendService.class);
        friendLiveTools = new AIFriendLiveTools(friendService, currentUserService);

        when(currentUserService.getCurrentUserId()).thenReturn(CURRENT_USER_ID);
        when(friendService.getAllFriends(CURRENT_USER_ID)).thenReturn(List.of());
    }

    @Test
    void getFriendLiveLocation_singleAccessibleFriendAutoSelects() {
        UUID friendId = UUID.randomUUID();
        FriendInfoDTO friend = friend(friendId, "alex@example.com", "Alex", true);
        when(friendService.getAllFriends(CURRENT_USER_ID)).thenReturn(List.of(friend));
        when(friendService.getFriendLocation(CURRENT_USER_ID, friendId)).thenReturn(gpsPoint(friendId, Instant.now()));

        var result = friendLiveTools.getFriendLiveLocation(null);

        assertEquals(friendId, result.getFriendId());
        assertEquals("alex@example.com", result.getEmail());
        assertTrue(result.getLatitude() != null && result.getLongitude() != null);
        assertTrue(Boolean.TRUE.equals(result.getLiveNow()));
        assertTrue(Boolean.FALSE.equals(result.getStale()));
        assertTrue(result.getSecondsAgo() != null && result.getSecondsAgo() >= 0L);
    }

    @Test
    void getFriendLiveLocation_requiresFriendSelectionWhenMultiple() {
        when(friendService.getAllFriends(CURRENT_USER_ID)).thenReturn(List.of(
                friend(UUID.randomUUID(), "a@example.com", "Alex", true),
                friend(UUID.randomUUID(), "b@example.com", "Bob", true)
        ));

        AIToolException error = assertThrows(AIToolException.class,
                () -> friendLiveTools.getFriendLiveLocation(null));

        assertEquals("LIVE_FRIEND_SELECTION_REQUIRED", error.getCode());
    }

    @Test
    void getFriendLiveLocation_deniesWhenFriendDoesNotShareLiveLocation() {
        UUID friendId = UUID.randomUUID();
        when(friendService.getAllFriends(CURRENT_USER_ID)).thenReturn(List.of(
                friend(friendId, "alex@example.com", "Alex", false)
        ));

        AIToolException error = assertThrows(AIToolException.class,
                () -> friendLiveTools.getFriendLiveLocation("alex@example.com"));

        assertEquals("LIVE_PERMISSION_DENIED", error.getCode());
    }

    @Test
    void getFriendLiveLocation_ambiguousNameReturnsStructuredError() {
        when(friendService.getAllFriends(CURRENT_USER_ID)).thenReturn(List.of(
                friend(UUID.randomUUID(), "a1@example.com", "Alex", true),
                friend(UUID.randomUUID(), "a2@example.com", "Alex", true)
        ));

        AIToolException error = assertThrows(AIToolException.class,
                () -> friendLiveTools.getFriendLiveLocation("Alex"));

        assertEquals("AMBIGUOUS_FRIEND", error.getCode());
    }

    @Test
    void listAccessibleLiveFriends_returnsOnlyAllowedFriends() {
        when(friendService.getAllFriends(CURRENT_USER_ID)).thenReturn(List.of(
                friend(UUID.randomUUID(), "yes@example.com", "Yes", true),
                friend(UUID.randomUUID(), "no@example.com", "No", false)
        ));

        var result = friendLiveTools.listAccessibleLiveFriends();

        assertEquals(1, result.size());
        assertEquals("yes@example.com", result.get(0).getEmail());
    }

    @Test
    void getFriendLiveLocation_oldPointIsMarkedStaleAndLastKnown() {
        UUID friendId = UUID.randomUUID();
        FriendInfoDTO friend = friend(friendId, "alex@example.com", "Alex", true);
        when(friendService.getAllFriends(CURRENT_USER_ID)).thenReturn(List.of(friend));
        when(friendService.getFriendLocation(CURRENT_USER_ID, friendId))
                .thenReturn(gpsPoint(friendId, Instant.now().minus(Duration.ofDays(10))));

        var result = friendLiveTools.getFriendLiveLocation("alex@example.com");

        assertTrue(Boolean.FALSE.equals(result.getLiveNow()));
        assertTrue(Boolean.TRUE.equals(result.getStale()));
        assertTrue(result.getSecondsAgo() != null && result.getSecondsAgo() >= Duration.ofDays(10).minusMinutes(1).getSeconds());
    }

    private FriendInfoDTO friend(UUID friendId, String email, String fullName, boolean sharesLive) {
        return FriendInfoDTO.builder()
                .userId(CURRENT_USER_ID)
                .friendId(friendId)
                .email(email)
                .fullName(fullName)
                .lastLocation("Kyiv")
                .friendSharesLiveLocation(sharesLive)
                .build();
    }

    private GpsPointEntity gpsPoint(UUID friendId, Instant timestamp) {
        UserEntity friendUser = UserEntity.builder().id(friendId).email("alex@example.com").fullName("Alex").build();
        GpsPointEntity point = new GpsPointEntity();
        point.setUser(friendUser);
        point.setCoordinates(GeoUtils.createPoint(30.5234, 50.4501));
        point.setTimestamp(timestamp);
        point.setAccuracy(12.0);
        point.setAltitude(150.0);
        point.setVelocity(0.0);
        return point;
    }
}
