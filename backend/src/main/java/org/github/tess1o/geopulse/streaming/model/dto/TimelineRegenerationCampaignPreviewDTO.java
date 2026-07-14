package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TimelineRegenerationCampaignPreviewDTO {
    private Instant affectedFrom;
    private int affectedUsers;
}
