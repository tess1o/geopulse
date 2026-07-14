package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TimelineRegenerationCampaignDetailDTO {
    private TimelineRegenerationCampaignSummaryDTO campaign;
    private List<TimelineRegenerationCampaignUserDTO> failedUsers;
}
