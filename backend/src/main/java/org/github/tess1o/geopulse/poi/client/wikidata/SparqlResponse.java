package org.github.tess1o.geopulse.poi.client.wikidata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Minimal model of the SPARQL JSON results format (https://www.w3.org/TR/sparql11-results-json/).
 *
 * <p>Bindings are kept as a map of variable name to value so the shape of a query can
 * change without touching this class.
 */
@Data
// Real responses also carry a "head" block (the variable list) we have no use for.
@JsonIgnoreProperties(ignoreUnknown = true)
public class SparqlResponse {

    private Results results;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Results {
        private List<Map<String, Binding>> bindings;
    }

    /** A single value; real rows also carry {@code type}, {@code datatype} and {@code xml:lang}. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Binding {
        private String value;
    }
}
