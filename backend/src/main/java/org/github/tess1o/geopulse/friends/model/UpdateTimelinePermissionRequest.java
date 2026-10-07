package org.github.tess1o.geopulse.friends.model;

import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record UpdateTimelinePermissionRequest(@Schema(examples = "true") @NotNull Boolean shareTimeline) {
}
