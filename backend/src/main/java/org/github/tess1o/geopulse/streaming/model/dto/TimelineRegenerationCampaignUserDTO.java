package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Builder;
import lombok.Data;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineRegenerationCampaignUserStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class TimelineRegenerationCampaignUserDTO {
    private Long id;
    private UUID userId;
    private String email;
    private String fullName;
    private TimelineRegenerationCampaignUserStatus status;
    private UUID jobId;
    private int attempts;
    private String lastError;
    private Instant nextAttemptAt;
    private Instant claimedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant updatedAt;
}
