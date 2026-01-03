package org.github.tess1o.geopulse.streaming.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.friends.repository.FriendshipRepository;
import org.github.tess1o.geopulse.friends.repository.UserFriendPermissionRepository;
import org.github.tess1o.geopulse.streaming.model.dto.MultiUserTimelineDTO;
import org.github.tess1o.geopulse.streaming.model.dto.MovementTimelineDTO;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Service for fetching and aggregating timelines from multiple users.
 * Handles authorization checks and parallel timeline fetching.
 */
@ApplicationScoped
@Slf4j
public class MultiUserTimelineService {

    // Color palette for user differentiation (matches frontend mapHelpers.js)
    private static final List<String> USER_COLORS = Arrays.asList(
            "#3B82F6", // Blue
            "#10B981", // Green
            "#F59E0B", // Amber
            "#EF4444", // Red
            "#8B5CF6", // Purple
            "#EC4899", // Pink
            "#14B8A6", // Teal
            "#F97316", // Orange
            "#6366F1", // Indigo
            "#84CC16"  // Lime
    );

    @Inject
    StreamingTimelineAggregator timelineAggregator;

    @Inject
    FriendshipRepository friendshipRepository;

    @Inject
    UserFriendPermissionRepository permissionRepository;

    @Inject
    UserRepository userRepository;

    /**
     * Get timeline data for multiple users with authorization checks.
     *
     * @param requestingUserId The user making the request
     * @param startTime        Start of time range
     * @param endTime          End of time range
     * @param userIds          List of user IDs to fetch timelines for (optional)
     * @return Multi-user timeline DTO
     */
    public MultiUserTimelineDTO getMultiUserTimeline(
            UUID requestingUserId,
            Instant startTime,
            Instant endTime,
            List<UUID> userIds
    ) {
        log.info("Fetching multi-user timeline for user {} from {} to {}", requestingUserId, startTime, endTime);

        // Determine which users to fetch
        List<UUID> targetUserIds = determineTargetUsers(requestingUserId, userIds);

        log.debug("Fetching timelines for {} users", targetUserIds.size());

        // Validate all friendships and permissions
        validateAccess(requestingUserId, targetUserIds);

        // Fetch timelines in parallel
        List<MultiUserTimelineDTO.UserTimelineDTO> userTimelines = fetchTimelinesInParallel(
                targetUserIds,
                startTime,
                endTime
        );

        // Build and return response
        return MultiUserTimelineDTO.builder()
                .requestingUserId(requestingUserId)
                .timelines(userTimelines)
                .startTime(startTime)
                .endTime(endTime)
                .generatedAt(Instant.now())
                .build();
    }

    /**
     * Determine which users' timelines to fetch.
     * If userIds is null/empty, fetch all friends who granted permission + requesting user.
     */
    private List<UUID> determineTargetUsers(UUID requestingUserId, List<UUID> userIds) {
        if (userIds != null && !userIds.isEmpty()) {
            // Specific users requested
            return userIds;
        } else {
            // Fetch all friends who granted permission + requesting user
            List<UUID> friendsWithPermission = permissionRepository.findFriendsWhoSharedTimelineWithUser(requestingUserId);
            List<UUID> targetUsers = new ArrayList<>(friendsWithPermission);
            targetUsers.add(requestingUserId); // Always include requesting user
            return targetUsers;
        }
    }

    /**
     * Validate that requesting user can access all target users' timelines.
     */
    private void validateAccess(UUID requestingUserId, List<UUID> targetUserIds) {
        for (UUID targetUserId : targetUserIds) {
            // User can always access their own timeline
            if (targetUserId.equals(requestingUserId)) {
                continue;
            }

            // Check friendship exists
            if (!friendshipRepository.existsFriendship(requestingUserId, targetUserId)) {
                log.warn("User {} attempted to access timeline of non-friend {}", requestingUserId, targetUserId);
                throw new ForbiddenException("Not authorized to view timeline of user: " + targetUserId);
            }

            // Check timeline permission
            if (!permissionRepository.hasTimelinePermission(targetUserId, requestingUserId)) {
                log.warn("User {} attempted to access timeline of {} without permission", requestingUserId, targetUserId);
                throw new ForbiddenException("User has not granted timeline access: " + targetUserId);
            }
        }

        log.debug("Access validation passed for {} users", targetUserIds.size());
    }

    /**
     * Fetch timelines for all users sequentially.
     * Note: Sequential execution is used to maintain CDI request context.
     * Performance is acceptable for small user counts (5-10 users).
     */
    @Transactional
    public List<MultiUserTimelineDTO.UserTimelineDTO> fetchTimelinesInParallel(
            List<UUID> targetUserIds,
            Instant startTime,
            Instant endTime
    ) {
        List<MultiUserTimelineDTO.UserTimelineDTO> results = new ArrayList<>();

        for (int i = 0; i < targetUserIds.size(); i++) {
            UUID userId = targetUserIds.get(i);
            String color = assignColor(i);

            try {
                MultiUserTimelineDTO.UserTimelineDTO userTimeline =
                        fetchUserTimeline(userId, startTime, endTime, color);
                results.add(userTimeline);
            } catch (Exception e) {
                log.error("Error fetching timeline for user {}", userId, e);
                throw new RuntimeException("Failed to fetch timeline for user: " + userId, e);
            }
        }

        log.debug("Successfully fetched {} user timelines", results.size());
        return results;
    }

    /**
     * Fetch timeline for a single user.
     */
    private MultiUserTimelineDTO.UserTimelineDTO fetchUserTimeline(UUID userId, Instant startTime, Instant endTime, String color) {
        log.debug("Fetching timeline for user {} with color {}", userId, color);

        // Get user info
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            log.error("User not found: {}", userId);
            throw new RuntimeException("User not found: " + userId);
        }

        // Fetch timeline
        MovementTimelineDTO timeline = timelineAggregator.getTimelineFromDb(userId, startTime, endTime);

        // Calculate stats
        MultiUserTimelineDTO.TimelineStats stats = calculateStats(timeline);

        return MultiUserTimelineDTO.UserTimelineDTO.builder()
                .userId(userId)
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .assignedColor(color)
                .timeline(timeline)
                .stats(stats)
                .build();
    }

    /**
     * Calculate summary statistics for a timeline.
     */
    private MultiUserTimelineDTO.TimelineStats calculateStats(MovementTimelineDTO timeline) {
        long totalDistance = timeline.getTrips() != null
                ? timeline.getTrips().stream().mapToLong(trip -> trip.getDistanceMeters()).sum()
                : 0;

        long totalTravelTime = timeline.getTrips() != null
                ? timeline.getTrips().stream().mapToLong(trip -> trip.getTripDuration()).sum()
                : 0;

        return MultiUserTimelineDTO.TimelineStats.builder()
                .totalStays(timeline.getStaysCount())
                .totalTrips(timeline.getTripsCount())
                .totalDataGaps(timeline.getDataGapsCount())
                .totalDistanceMeters(totalDistance)
                .totalTravelTimeSeconds(totalTravelTime)
                .build();
    }

    /**
     * Assign color from palette based on index.
     */
    private String assignColor(int index) {
        return USER_COLORS.get(index % USER_COLORS.size());
    }
}
