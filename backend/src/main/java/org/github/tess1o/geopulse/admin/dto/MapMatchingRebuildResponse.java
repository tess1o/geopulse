package org.github.tess1o.geopulse.admin.dto;

import org.github.tess1o.geopulse.mapmatching.model.MapMatchingRebuildMode;

public record MapMatchingRebuildResponse(MapMatchingRebuildMode mode,
                                         long queuedUsers,
                                         long affectedTargets,
                                         long purgedDetachedTargets) {
}
