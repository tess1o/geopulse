package org.github.tess1o.geopulse.mapmatching.model;

/**
 * Outcome of re-queuing map-matching targets that never produced a usable match.
 *
 * @param requeuedTargets  targets moved back to PENDING so the worker picks them up again
 * @param purgedDetachedTargets stale targets without a trip that were deleted instead of re-queued
 */
public record MapMatchingTargetReset(long requeuedTargets, long purgedDetachedTargets) {
}
