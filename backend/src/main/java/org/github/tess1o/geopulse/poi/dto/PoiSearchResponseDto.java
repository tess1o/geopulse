package org.github.tess1o.geopulse.poi.dto;

import java.util.List;

/**
 * Result of a POI discovery query.
 *
 * <p>Carries the data attribution that must be displayed wherever these results appear.
 */
public record PoiSearchResponseDto(
        List<PoiDto> results,
        String dataAttribution,
        /** True when the results came from cache rather than a fresh provider query. */
        boolean cached
) {
}
