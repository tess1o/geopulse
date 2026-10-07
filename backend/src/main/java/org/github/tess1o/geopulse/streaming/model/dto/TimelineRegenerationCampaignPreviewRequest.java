package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Data;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class TimelineRegenerationCampaignPreviewRequest {
    @Schema(examples = "2025-01-01T00:00:00Z")
    private Instant affectedFrom;
}
