package org.github.tess1o.geopulse.trips.model.dto;

/**
 * Outcome of the external (live provider) leg of a trip plan search.
 *
 * <p>Exists so a failed or unconfigured provider is distinguishable from a genuine
 * "nothing matched" result. Before this, every failure collapsed into an empty list,
 * which is why a misconfigured instance looked identical to a successful empty search.
 */
public enum PlanSearchExternalStatus {
    /** At least one provider answered. An empty result list here is genuine. */
    OK,
    /** No provider is configured for forward search, so no external results were attempted. */
    DISABLED,
    /** A provider was attempted and failed (network, HTTP error, circuit open, ...). */
    FAILED
}
