package org.github.tess1o.geopulse.ai.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.model.AIFriendLiveCandidateDTO;
import org.github.tess1o.geopulse.ai.model.AIFriendLiveLocationDTO;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.friends.model.FriendInfoDTO;
import org.github.tess1o.geopulse.friends.service.FriendService;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.user.exceptions.NotAuthorizedUserException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * AI tools for friend live location access.
 */
@ApplicationScoped
@Slf4j
public class AIFriendLiveTools {

    private static final int MAX_CANDIDATES_IN_ERROR = 10;
    private static final long LIVE_NOW_THRESHOLD_SECONDS = 5 * 60;

    private final FriendService friendService;
    private final CurrentUserService currentUserService;

    @Inject
    public AIFriendLiveTools(FriendService friendService, CurrentUserService currentUserService) {
        this.friendService = friendService;
        this.currentUserService = currentUserService;
    }

    public List<AIFriendLiveCandidateDTO> listAccessibleLiveFriends() {
        UUID currentUserId = currentUserService.getCurrentUserId();
        return friendService.getAllFriends(currentUserId).stream()
                .filter(friend -> Boolean.TRUE.equals(friend.getFriendSharesLiveLocation()))
                .map(this::toCandidate)
                .sorted(Comparator
                        .comparing((AIFriendLiveCandidateDTO f) -> normalize(f.getFullName()))
                        .thenComparing(f -> normalize(f.getEmail())))
                .toList();
    }

    public AIFriendLiveLocationDTO getFriendLiveLocation(String targetUser) {
        UUID currentUserId = currentUserService.getCurrentUserId();
        log.info("🔧 AI TOOL EXECUTED: getFriendLiveLocation({})", targetUser);

        if (looksLikeMultiUserRequest(targetUser)) {
            throw new AIToolException(
                    "MULTI_USER_NOT_SUPPORTED",
                    "Multi-user friend queries are not supported in v1. Ask about one friend at a time."
            );
        }

        List<FriendInfoDTO> allFriends = friendService.getAllFriends(currentUserId);
        List<FriendInfoDTO> accessibleFriends = allFriends.stream()
                .filter(friend -> Boolean.TRUE.equals(friend.getFriendSharesLiveLocation()))
                .toList();

        FriendInfoDTO selectedFriend = selectFriend(allFriends, accessibleFriends, targetUser);

        GpsPointEntity livePoint;
        try {
            livePoint = friendService.getFriendLocation(currentUserId, selectedFriend.getFriendId());
        } catch (NotAuthorizedUserException e) {
            throw new AIToolException(
                    "LIVE_PERMISSION_DENIED",
                    "Friend has not granted live location access.",
                    Map.of("friend", toCandidate(selectedFriend))
            );
        } catch (Exception e) {
            log.error("Failed to fetch friend live location", e);
            throw new AIToolException("LIVE_LOCATION_QUERY_FAILED", "Failed to fetch friend live location.");
        }

        if (livePoint == null) {
            throw new AIToolException(
                    "LIVE_LOCATION_NOT_FOUND",
                    "No live location found for this friend right now.",
                    Map.of("friend", toCandidate(selectedFriend))
            );
        }

        Instant timestamp = livePoint.getTimestamp();
        long secondsAgo = timestamp != null
                ? Math.max(0, java.time.Duration.between(timestamp, Instant.now()).getSeconds())
                : Long.MAX_VALUE;
        boolean liveNow = timestamp != null && secondsAgo <= LIVE_NOW_THRESHOLD_SECONDS;
        boolean stale = !liveNow;

        return AIFriendLiveLocationDTO.builder()
                .friendId(selectedFriend.getFriendId())
                .email(selectedFriend.getEmail())
                .fullName(selectedFriend.getFullName())
                .locationName(selectedFriend.getLastLocation())
                .timestamp(timestamp)
                .secondsAgo(secondsAgo)
                .liveNow(liveNow)
                .stale(stale)
                .latitude(livePoint.getLatitude())
                .longitude(livePoint.getLongitude())
                .accuracy(livePoint.getAccuracy())
                .altitude(livePoint.getAltitude())
                .speedKmh(livePoint.getVelocity())
                .build();
    }

    private FriendInfoDTO selectFriend(List<FriendInfoDTO> allFriends,
                                       List<FriendInfoDTO> accessibleFriends,
                                       String targetUser) {
        if (isBlank(targetUser)) {
            if (accessibleFriends.isEmpty()) {
                throw new AIToolException(
                        "NO_FRIEND_LIVE_ACCESS",
                        "None of your friends have shared live location with you."
                );
            }
            if (accessibleFriends.size() == 1) {
                return accessibleFriends.get(0);
            }
            throw new AIToolException(
                    "LIVE_FRIEND_SELECTION_REQUIRED",
                    "Multiple friends shared live location. Specify which friend by email or full name.",
                    Map.of("candidates", limitCandidates(accessibleFriends))
            );
        }

        String normalizedQuery = normalize(targetUser);

        List<FriendInfoDTO> exactMatches = allFriends.stream()
                .filter(friend -> normalize(friend.getEmail()).equals(normalizedQuery)
                        || normalize(friend.getFullName()).equals(normalizedQuery))
                .toList();

        if (exactMatches.size() > 1) {
            throw ambiguousFriendError(targetUser, exactMatches);
        }
        if (exactMatches.size() == 1) {
            return ensureLiveAccess(targetUser, exactMatches.get(0));
        }

        List<FriendInfoDTO> fuzzyMatches = allFriends.stream()
                .filter(friend -> normalize(friend.getEmail()).contains(normalizedQuery)
                        || normalize(friend.getFullName()).contains(normalizedQuery))
                .toList();

        if (fuzzyMatches.size() > 1) {
            throw ambiguousFriendError(targetUser, fuzzyMatches);
        }
        if (fuzzyMatches.size() == 1) {
            return ensureLiveAccess(targetUser, fuzzyMatches.get(0));
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

    private FriendInfoDTO ensureLiveAccess(String targetUser, FriendInfoDTO friend) {
        if (Boolean.TRUE.equals(friend.getFriendSharesLiveLocation())) {
            return friend;
        }
        throw new AIToolException(
                "LIVE_PERMISSION_DENIED",
                "Friend exists but has not granted live location access.",
                Map.of(
                        "targetUser", targetUser,
                        "friend", toCandidate(friend)
                )
        );
    }

    private AIToolException ambiguousFriendError(String targetUser, List<FriendInfoDTO> matches) {
        return new AIToolException(
                "AMBIGUOUS_FRIEND",
                "Multiple friends match that name. Specify email or full name more precisely.",
                Map.of(
                        "targetUser", targetUser,
                        "candidates", limitCandidates(matches)
                )
        );
    }

    private List<AIFriendLiveCandidateDTO> limitCandidates(List<FriendInfoDTO> friends) {
        return friends.stream()
                .map(this::toCandidate)
                .limit(MAX_CANDIDATES_IN_ERROR)
                .toList();
    }

    private AIFriendLiveCandidateDTO toCandidate(FriendInfoDTO friend) {
        UUID friendId = friend.getFriendId() != null ? friend.getFriendId() : friend.getUserId();
        return AIFriendLiveCandidateDTO.builder()
                .friendId(friendId)
                .email(friend.getEmail())
                .fullName(friend.getFullName())
                .liveLocationAccessGranted(Boolean.TRUE.equals(friend.getFriendSharesLiveLocation()))
                .lastSeen(friend.getLastSeen())
                .lastLocation(friend.getLastLocation())
                .build();
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

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
