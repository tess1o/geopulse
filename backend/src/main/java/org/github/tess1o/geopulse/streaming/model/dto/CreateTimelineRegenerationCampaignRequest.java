package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Data;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class CreateTimelineRegenerationCampaignRequest {
    @Schema(examples = "stay-detection-2025-06")
    private String campaignKey;
    @Schema(examples = "2025-01-01T00:00:00Z")
    private Instant affectedFrom;
    @Schema(examples = "Changed default stay detection radius")
    private String reason;
}
