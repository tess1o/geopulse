package org.github.tess1o.geopulse.streaming.model.dto;

import java.util.UUID;

public record TimelineEditMutationResponse(TimelineEditDTO edit, UUID timelineJobId, boolean timelineJobQueued) {
}
