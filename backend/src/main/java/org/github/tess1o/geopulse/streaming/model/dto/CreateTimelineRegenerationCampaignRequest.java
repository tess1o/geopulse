package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class CreateTimelineRegenerationCampaignRequest {
    private String campaignKey;
    private Instant affectedFrom;
    private String reason;
}
