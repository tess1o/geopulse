package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for multi-user timeline data.
 * Contains timelines for multiple users with their associated metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultiUserTimelineDTO {
    private UUID requestingUserId;
    private List<UserTimelineDTO> timelines;
    private Instant startTime;
    private Instant endTime;
    private Instant generatedAt;

    /**
     * Individual user's timeline data with metadata.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserTimelineDTO {
        private UUID userId;
        private String fullName;
        private String email;
        private String avatar;
        private String assignedColor;  // Color assigned by backend for consistency
        private MovementTimelineDTO timeline;
        private TimelineStats stats;
    }

    /**
     * Summary statistics for a user's timeline.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TimelineStats {
        private int totalStays;
        private int totalTrips;
        private int totalDataGaps;
        private long totalDistanceMeters;
        private long totalTravelTimeSeconds;
    }
}
