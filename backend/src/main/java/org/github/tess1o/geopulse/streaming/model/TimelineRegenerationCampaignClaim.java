package org.github.tess1o.geopulse.streaming.model;

import java.time.Instant;
import java.util.UUID;

public record TimelineRegenerationCampaignClaim(
        Long campaignUserId,
        UUID campaignId,
        String campaignKey,
        UUID userId,
        Instant affectedFrom,
        int attempts
) {
}
