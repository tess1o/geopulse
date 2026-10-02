package org.github.tess1o.geopulse.admin.dto;

/**
 * Result of testing the two place-discovery endpoints.
 *
 * <p>They are reported separately because they fail independently: Wikidata can be
 * unreachable while Commons is fine, which would mean places load without photos.
 */
public record PoiTestResponse(
        boolean success,
        boolean wikidataSuccess,
        String wikidataEndpoint,
        String wikidataDetail,
        boolean commonsSuccess,
        String commonsEndpoint,
        String commonsDetail
) {
}
