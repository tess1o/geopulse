package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class TimelineRegenerationCampaignPreviewRequest {
    private Instant affectedFrom;
}
