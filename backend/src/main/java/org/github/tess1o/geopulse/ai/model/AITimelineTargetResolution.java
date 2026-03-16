package org.github.tess1o.geopulse.ai.model;

import java.util.UUID;

/**
 * Result of resolving the effective timeline owner for an AI tool request.
 */
public record AITimelineTargetResolution(
        UUID timelineOwnerUserId,
        AITimelineTargetScope targetScope,
        String resolvedLabel
) {
}
