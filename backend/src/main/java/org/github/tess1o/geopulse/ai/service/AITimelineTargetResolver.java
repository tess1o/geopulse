package org.github.tess1o.geopulse.ai.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.model.AITimelineFriendCandidateDTO;
import org.github.tess1o.geopulse.ai.model.AITimelineTargetResolution;
import org.github.tess1o.geopulse.ai.model.AITimelineTargetScope;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.friends.model.FriendInfoDTO;
import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.friends.repository.UserFriendPermissionRepository;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves which user's timeline an AI query should target.
 */
@ApplicationScoped
@Slf4j
public class AITimelineTargetResolver {

    private static final int MAX_CANDIDATES_IN_ERROR = 10;

    private final CurrentUserService currentUserService;
    private final FriendshipRepository friendshipRepository;
    private final UserFriendPermissionRepository permissionRepository;
    private final UserRepository userRepository;

    @Inject
    public AITimelineTargetResolver(CurrentUserService currentUserService,
                                    FriendshipRepository friendshipRepository,
                                    UserFriendPermissionRepository permissionRepository,
                                    UserRepository userRepository) {
        this.currentUserService = currentUserService;
        this.friendshipRepository = friendshipRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
    }

    public AITimelineTargetResolution resolveTarget(String targetScopeValue, String targetUser) {
        UUID currentUserId = currentUserService.getCurrentUserId();
        AITimelineTargetScope targetScope = parseScope(targetScopeValue);

        if (targetScope == AITimelineTargetScope.SELF) {
            return new AITimelineTargetResolution(currentUserId, AITimelineTargetScope.SELF, "self");
        }

        if (looksLikeMultiUserRequest(targetUser)) {
            throw new AIToolException(
                    "MULTI_USER_NOT_SUPPORTED",
                    "Multi-user friend queries are not supported in v1. Ask about one friend at a time."
            );
        }

        List<AITimelineFriendCandidateDTO> allFriends = loadAllFriendCandidates(currentUserId);
        List<AITimelineFriendCandidateDTO> accessibleFriends = allFriends.stream()
                .filter(AITimelineFriendCandidateDTO::isTimelineAccessGranted)
                .toList();

        if (isBlank(targetUser)) {
            if (accessibleFriends.isEmpty()) {
                throw new AIToolException(
                        "NO_FRIEND_TIMELINE_ACCESS",
                        "None of your friends have shared timeline access with you."
                );
            }
            if (accessibleFriends.size() == 1) {
                AITimelineFriendCandidateDTO friend = accessibleFriends.get(0);
                validateFriendAccess(currentUserId, friend);
                return toFriendResolution(friend);
            }
            throw new AIToolException(
                    "FRIEND_SELECTION_REQUIRED",
                    "Multiple friends shared timeline access. Specify which friend by email or full name.",
                    Map.of("candidates", limitCandidates(accessibleFriends))
            );
        }

        String normalizedQuery = normalize(targetUser);
        List<AITimelineFriendCandidateDTO> exactMatches = findExactMatches(allFriends, normalizedQuery);
        if (exactMatches.size() > 1) {
            throw ambiguousFriendError(targetUser, exactMatches);
        }
        if (exactMatches.size() == 1) {
            return resolveSingleFriendMatch(currentUserId, exactMatches.get(0));
        }

        List<AITimelineFriendCandidateDTO> fuzzyMatches = findFuzzyMatches(allFriends, normalizedQuery);
        if (fuzzyMatches.size() > 1) {
            throw ambiguousFriendError(targetUser, fuzzyMatches);
        }
        if (fuzzyMatches.size() == 1) {
            return resolveSingleFriendMatch(currentUserId, fuzzyMatches.get(0));
        }

        if (looksLikeEmail(normalizedQuery) && userRepository.findByEmailIgnoreCase(normalizedQuery).isPresent()) {
            throw new AIToolException(
                    "NOT_FRIEND",
                    "That user exists but is not your friend.",
                    Map.of("targetUser", targetUser)
            );
        }

        throw new AIToolException(
                "FRIEND_NOT_FOUND",
                "Could not match that friend. Provide a more specific email or full name.",
                Map.of(
                        "targetUser", targetUser,
                        "candidates", limitCandidates(accessibleFriends)
                )
        );
    }

    public List<AITimelineFriendCandidateDTO> listAccessibleTimelineFriends() {
        UUID currentUserId = currentUserService.getCurrentUserId();
        return loadAllFriendCandidates(currentUserId).stream()
                .filter(AITimelineFriendCandidateDTO::isTimelineAccessGranted)
                .toList();
    }

    private AITimelineTargetResolution resolveSingleFriendMatch(UUID currentUserId, AITimelineFriendCandidateDTO match) {
        validateFriendAccess(currentUserId, match);
        return toFriendResolution(match);
    }

    private AITimelineTargetResolution toFriendResolution(AITimelineFriendCandidateDTO friend) {
        String resolvedLabel = !isBlank(friend.getFullName()) ? friend.getFullName() : friend.getEmail();
        return new AITimelineTargetResolution(friend.getUserId(), AITimelineTargetScope.FRIEND, resolvedLabel);
    }

    private void validateFriendAccess(UUID currentUserId, AITimelineFriendCandidateDTO friend) {
        UUID friendId = friend.getUserId();

        if (!friendshipRepository.existsFriendship(currentUserId, friendId)) {
            throw new AIToolException(
                    "NOT_FRIEND",
                    "Not authorized: requested user is not your friend.",
                    Map.of("friend", friend)
            );
        }

        boolean hasPermission = permissionRepository.hasTimelinePermission(friendId, currentUserId);
        if (!hasPermission) {
            throw new AIToolException(
                    "TIMELINE_PERMISSION_DENIED",
                    "Friend has not granted timeline access.",
                    Map.of("friend", friend)
            );
        }
    }

    private AIToolException ambiguousFriendError(String targetUser, List<AITimelineFriendCandidateDTO> matches) {
        return new AIToolException(
                "AMBIGUOUS_FRIEND",
                "Multiple friends match that name. Specify email or full name more precisely.",
                Map.of(
                        "targetUser", targetUser,
                        "candidates", limitCandidates(matches)
                )
        );
    }

    private List<AITimelineFriendCandidateDTO> loadAllFriendCandidates(UUID currentUserId) {
        List<FriendInfoDTO> friends = friendshipRepository.findFriends(currentUserId);
        Map<UUID, AITimelineFriendCandidateDTO> unique = new LinkedHashMap<>();

        for (FriendInfoDTO friend : friends) {
            UUID friendId = friend.getFriendId() != null ? friend.getFriendId() : friend.getUserId();
            if (friendId == null) {
                continue;
            }
            unique.put(friendId, AITimelineFriendCandidateDTO.builder()
                    .userId(friendId)
                    .email(friend.getEmail())
                    .fullName(friend.getFullName())
                    .timelineAccessGranted(Boolean.TRUE.equals(friend.getFriendSharesTimeline()))
                    .build());
        }

        return unique.values().stream()
                .sorted(Comparator
                        .comparing((AITimelineFriendCandidateDTO f) -> normalize(f.getFullName()))
                        .thenComparing(f -> normalize(f.getEmail())))
                .toList();
    }

    private List<AITimelineFriendCandidateDTO> findExactMatches(List<AITimelineFriendCandidateDTO> friends, String query) {
        return friends.stream()
                .filter(friend -> normalize(friend.getEmail()).equals(query)
                        || normalize(friend.getFullName()).equals(query))
                .toList();
    }

    private List<AITimelineFriendCandidateDTO> findFuzzyMatches(List<AITimelineFriendCandidateDTO> friends, String query) {
        return friends.stream()
                .filter(friend -> normalize(friend.getEmail()).contains(query)
                        || normalize(friend.getFullName()).contains(query))
                .toList();
    }

    private List<AITimelineFriendCandidateDTO> limitCandidates(List<AITimelineFriendCandidateDTO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<AITimelineFriendCandidateDTO> unique = candidates.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                AITimelineFriendCandidateDTO::getUserId,
                                candidate -> candidate,
                                (left, right) -> left,
                                LinkedHashMap::new
                        ),
                        map -> new ArrayList<>(map.values())
                ));

        return unique.stream()
                .limit(MAX_CANDIDATES_IN_ERROR)
                .toList();
    }

    private AITimelineTargetScope parseScope(String targetScopeValue) {
        if (isBlank(targetScopeValue)) {
            return AITimelineTargetScope.SELF;
        }

        String normalized = targetScopeValue.trim().toUpperCase(Locale.ENGLISH);
        try {
            return AITimelineTargetScope.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid AI target scope: {}", targetScopeValue);
            throw new AIToolException(
                    "INVALID_TARGET_SCOPE",
                    "Invalid targetScope. Allowed values: SELF or FRIEND.",
                    Map.of("targetScope", targetScopeValue)
            );
        }
    }

    private boolean looksLikeMultiUserRequest(String targetUser) {
        if (isBlank(targetUser)) {
            return false;
        }
        String normalized = targetUser.toLowerCase(Locale.ENGLISH);
        return normalized.contains(",")
                || normalized.contains(";")
                || normalized.contains("|")
                || normalized.contains(" and ")
                || normalized.contains(" & ")
                || normalized.contains(" plus ");
    }

    private boolean looksLikeEmail(String value) {
        return value != null && value.contains("@") && !value.contains(" ");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
