package org.github.tess1o.geopulse.ai.service;

import org.github.tess1o.geopulse.ai.model.AITimelineTargetResolution;
import org.github.tess1o.geopulse.ai.model.AITimelineTargetScope;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.friends.model.FriendInfoDTO;
import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.friends.repository.UserFriendPermissionRepository;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@Tag("unit")
class AITimelineTargetResolverTest {

    private static final UUID CURRENT_USER_ID = UUID.randomUUID();

    private CurrentUserService currentUserService;
    private FriendshipRepository friendshipRepository;
    private UserFriendPermissionRepository permissionRepository;
    private UserRepository userRepository;
    private AITimelineTargetResolver resolver;

    @BeforeEach
    void setUp() {
        currentUserService = Mockito.mock(CurrentUserService.class);
        friendshipRepository = Mockito.mock(FriendshipRepository.class);
        permissionRepository = Mockito.mock(UserFriendPermissionRepository.class);
        userRepository = Mockito.mock(UserRepository.class);

        resolver = new AITimelineTargetResolver(
                currentUserService,
                friendshipRepository,
                permissionRepository,
                userRepository
        );

        when(currentUserService.getCurrentUserId()).thenReturn(CURRENT_USER_ID);
        when(friendshipRepository.findFriends(CURRENT_USER_ID)).thenReturn(List.of());
        when(userRepository.findByEmailIgnoreCase(Mockito.anyString())).thenReturn(Optional.empty());
    }

    @Test
    void resolveTarget_defaultsToSelf() {
        AITimelineTargetResolution resolution = resolver.resolveTarget(null, null);

        assertEquals(CURRENT_USER_ID, resolution.timelineOwnerUserId());
        assertEquals(AITimelineTargetScope.SELF, resolution.targetScope());
    }

    @Test
    void resolveTarget_friendByExactEmail() {
        UUID friendId = UUID.randomUUID();
        FriendInfoDTO friend = friend(friendId, "alex@example.com", "Alex Johnson", true);

        when(friendshipRepository.findFriends(CURRENT_USER_ID)).thenReturn(List.of(friend));
        mockFriendAccess(friendId, true, true);

        AITimelineTargetResolution resolution = resolver.resolveTarget("FRIEND", "alex@example.com");

        assertEquals(friendId, resolution.timelineOwnerUserId());
        assertEquals(AITimelineTargetScope.FRIEND, resolution.targetScope());
    }

    @Test
    void resolveTarget_friendByExactFullName() {
        UUID friendId = UUID.randomUUID();
        FriendInfoDTO friend = friend(friendId, "alex@example.com", "Alex Johnson", true);

        when(friendshipRepository.findFriends(CURRENT_USER_ID)).thenReturn(List.of(friend));
        mockFriendAccess(friendId, true, true);

        AITimelineTargetResolution resolution = resolver.resolveTarget("FRIEND", "alex johnson");

        assertEquals(friendId, resolution.timelineOwnerUserId());
        assertEquals(AITimelineTargetScope.FRIEND, resolution.targetScope());
    }

    @Test
    void resolveTarget_uniqueFuzzyMatch() {
        UUID friendId = UUID.randomUUID();
        FriendInfoDTO friend = friend(friendId, "alex@example.com", "Alex Johnson", true);

        when(friendshipRepository.findFriends(CURRENT_USER_ID)).thenReturn(List.of(friend));
        mockFriendAccess(friendId, true, true);

        AITimelineTargetResolution resolution = resolver.resolveTarget("FRIEND", "alex");

        assertEquals(friendId, resolution.timelineOwnerUserId());
    }

    @Test
    void resolveTarget_ambiguousFriendThrowsStructuredError() {
        UUID friendA = UUID.randomUUID();
        UUID friendB = UUID.randomUUID();

        when(friendshipRepository.findFriends(CURRENT_USER_ID)).thenReturn(List.of(
                friend(friendA, "alex1@example.com", "Alex", true),
                friend(friendB, "alex2@example.com", "Alex", true)
        ));

        AIToolException error = assertThrows(AIToolException.class,
                () -> resolver.resolveTarget("FRIEND", "Alex"));

        assertEquals("AMBIGUOUS_FRIEND", error.getCode());
        assertNotNull(error.getDetails().get("candidates"));
    }

    @Test
    void resolveTarget_noMatchThrowsStructuredError() {
        UUID friendId = UUID.randomUUID();
        when(friendshipRepository.findFriends(CURRENT_USER_ID)).thenReturn(List.of(
                friend(friendId, "alex@example.com", "Alex", true)
        ));

        AIToolException error = assertThrows(AIToolException.class,
                () -> resolver.resolveTarget("FRIEND", "unknown-person"));

        assertEquals("FRIEND_NOT_FOUND", error.getCode());
    }

    @Test
    void resolveTarget_permissionDeniedThrowsStructuredError() {
        UUID friendId = UUID.randomUUID();
        FriendInfoDTO friend = friend(friendId, "alex@example.com", "Alex", false);

        when(friendshipRepository.findFriends(CURRENT_USER_ID)).thenReturn(List.of(friend));
        mockFriendAccess(friendId, true, false);

        AIToolException error = assertThrows(AIToolException.class,
                () -> resolver.resolveTarget("FRIEND", "alex@example.com"));

        assertEquals("TIMELINE_PERMISSION_DENIED", error.getCode());
    }

    @Test
    void resolveTarget_singleAccessibleFriendAutoSelectsWhenNoTargetUser() {
        UUID friendId = UUID.randomUUID();
        FriendInfoDTO friend = friend(friendId, "alex@example.com", "Alex", true);

        when(friendshipRepository.findFriends(CURRENT_USER_ID)).thenReturn(List.of(friend));
        mockFriendAccess(friendId, true, true);

        AITimelineTargetResolution resolution = resolver.resolveTarget("FRIEND", null);

        assertEquals(friendId, resolution.timelineOwnerUserId());
        assertEquals(AITimelineTargetScope.FRIEND, resolution.targetScope());
    }

    @Test
    void resolveTarget_multipleAccessibleFriendsWithoutTargetRequiresSelection() {
        UUID friendA = UUID.randomUUID();
        UUID friendB = UUID.randomUUID();

        when(friendshipRepository.findFriends(CURRENT_USER_ID)).thenReturn(List.of(
                friend(friendA, "a@example.com", "Alice", true),
                friend(friendB, "b@example.com", "Bob", true)
        ));

        AIToolException error = assertThrows(AIToolException.class,
                () -> resolver.resolveTarget("FRIEND", null));

        assertEquals("FRIEND_SELECTION_REQUIRED", error.getCode());
    }

    @Test
    void resolveTarget_multiFriendRequestRejected() {
        AIToolException error = assertThrows(AIToolException.class,
                () -> resolver.resolveTarget("FRIEND", "alice and bob"));

        assertEquals("MULTI_USER_NOT_SUPPORTED", error.getCode());
    }

    @Test
    void listAccessibleTimelineFriends_onlyReturnsSharedFriends() {
        UUID friendA = UUID.randomUUID();
        UUID friendB = UUID.randomUUID();

        when(friendshipRepository.findFriends(CURRENT_USER_ID)).thenReturn(List.of(
                friend(friendA, "a@example.com", "Alice", true),
                friend(friendB, "b@example.com", "Bob", false)
        ));

        List<?> result = resolver.listAccessibleTimelineFriends();

        assertEquals(1, result.size());
        assertTrue(result.toString().contains("a@example.com"));
    }

    private void mockFriendAccess(UUID friendId, boolean isFriend, boolean hasPermission) {
        when(friendshipRepository.existsFriendship(CURRENT_USER_ID, friendId)).thenReturn(isFriend);
        when(permissionRepository.hasTimelinePermission(friendId, CURRENT_USER_ID)).thenReturn(hasPermission);
    }

    private FriendInfoDTO friend(UUID friendId, String email, String fullName, boolean sharesTimeline) {
        return FriendInfoDTO.builder()
                .userId(CURRENT_USER_ID)
                .friendId(friendId)
                .email(email)
                .fullName(fullName)
                .friendSharesTimeline(sharesTimeline)
                .build();
    }
}
