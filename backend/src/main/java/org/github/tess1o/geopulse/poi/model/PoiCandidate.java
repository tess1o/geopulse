package org.github.tess1o.geopulse.poi.model;

/**
 * A POI as returned by a provider, before it is persisted or enriched with licence data.
 */
public record PoiCandidate(
        String externalId,
        String name,
        String description,
        double latitude,
        double longitude,
        /** Wikimedia Commons file name, e.g. {@code Tour Eiffel.jpg}. */
        String imageFile
) {
}
