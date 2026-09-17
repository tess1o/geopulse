package org.github.tess1o.geopulse.friends.model;

import jakarta.validation.constraints.NotNull;

public record UpdateTimelinePermissionRequest(@NotNull Boolean shareTimeline) {
}
