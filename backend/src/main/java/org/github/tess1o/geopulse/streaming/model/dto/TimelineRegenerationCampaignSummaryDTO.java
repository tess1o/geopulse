package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Builder;
import lombok.Data;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineRegenerationCampaignSource;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineRegenerationCampaignStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class TimelineRegenerationCampaignSummaryDTO {
    private UUID id;
    private String campaignKey;
    private Instant affectedFrom;
    private String reason;
    private TimelineRegenerationCampaignSource source;
    private TimelineRegenerationCampaignStatus status;
    private int totalUsers;
    private int pendingUsers;
    private int runningUsers;
    private int completedUsers;
    private int failedUsers;
    private int skippedUsers;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
}
