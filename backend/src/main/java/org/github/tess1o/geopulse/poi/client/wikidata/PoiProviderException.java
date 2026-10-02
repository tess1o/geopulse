package org.github.tess1o.geopulse.poi.client.wikidata;

/**
 * Raised when a POI provider cannot answer. Distinct from "answered with no results",
 * so the caller can tell a broken provider from an empty area.
 */
public class PoiProviderException extends RuntimeException {

    public PoiProviderException(String message) {
        super(message);
    }

    public PoiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
