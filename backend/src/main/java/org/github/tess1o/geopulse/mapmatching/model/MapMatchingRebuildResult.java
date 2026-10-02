package org.github.tess1o.geopulse.mapmatching.model;

/**
 * Result of an admin-triggered map-matching re-run.
 *
 * @param mode                  what the re-run covered
 * @param queuedUsers           user histories whose historical scan was restarted
 * @param affectedTargets       targets re-queued (UNSUCCESSFUL) or deleted (ALL)
 * @param purgedDetachedTargets stale targets without a trip that were deleted alongside the re-queue
 */
public record MapMatchingRebuildResult(MapMatchingRebuildMode mode,
                                       long queuedUsers,
                                       long affectedTargets,
                                       long purgedDetachedTargets) {
}
