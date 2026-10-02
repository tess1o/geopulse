package org.github.tess1o.geopulse.poi.client.wikidata;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.poi.client.PoiRestClientFactory;
import org.github.tess1o.geopulse.poi.model.PoiCandidate;
import org.github.tess1o.geopulse.poi.service.PoiConfigurationService;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Queries Wikidata for notable places that have an image, inside a bounding box.
 *
 * <p>Wikidata is a good fit for "what should I visit": items carry a label, a short
 * description and (via the P18 claim) a Wikimedia Commons image, so one query yields a
 * displayable card. The bounding-box service is the geospatial search primitive.
 */
@ApplicationScoped
@Slf4j
public class WikidataPoiClient {

    private static final String PROVIDER = "WIKIDATA";

    @Inject
    PoiRestClientFactory clientFactory;

    @Inject
    PoiConfigurationService config;

    public String providerName() {
        return PROVIDER;
    }

    /**
     * Placeholder-replaced SPARQL. The only interpolated values are numbers rendered with
     * {@link Locale#ROOT} and a language tag that has already been pattern-validated, so
     * no user-supplied text ever reaches the query.
     */
    private static String buildQuery(double south, double north, double west, double east,
                                     String language, int limit) {
        return """
                SELECT ?item ?itemLabel ?coord ?image ?desc WHERE {
                  SERVICE wikibase:box {
                    ?item wdt:P625 ?coord .
                    bd:serviceParam wikibase:cornerWest "Point(%s %s)"^^geo:wktLiteral .
                    bd:serviceParam wikibase:cornerEast "Point(%s %s)"^^geo:wktLiteral .
                  }
                  ?item wdt:P18 ?image .
                  OPTIONAL { ?item schema:description ?desc FILTER(lang(?desc) = "%s") }
                  SERVICE wikibase:label { bd:serviceParam wikibase:language "%s". }
                }
                LIMIT %d
                """.formatted(
                num(west), num(south),
                num(east), num(north),
                language, language, limit);
    }

    private static String num(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    public Uni<List<PoiCandidate>> searchBox(double south, double north, double west, double east, int limit) {
        String query = buildQuery(south, north, west, east, config.getLanguage(), limit);
        return clientFactory.wikidata()
                .query(query, config.getUserAgent(), "application/sparql-results+json")
                .map(WikidataPoiClient::mapResponse)
                .onFailure().transform(failure -> {
                    // Full trace: a silently empty result here is indistinguishable from
                    // "no notable places in this area", which is exactly the ambiguity
                    // this feature must not have.
                    log.warn("Wikidata POI query failed for box ({},{},{},{})",
                            south, north, west, east, failure);
                    return new PoiProviderException("Wikidata POI query failed", failure);
                });
    }

    private static List<PoiCandidate> mapResponse(SparqlResponse response) {
        List<PoiCandidate> results = new ArrayList<>();
        if (response == null || response.getResults() == null || response.getResults().getBindings() == null) {
            return results;
        }

        for (Map<String, SparqlResponse.Binding> row : response.getResults().getBindings()) {
            PoiCandidate candidate = toCandidate(row);
            if (candidate != null) {
                results.add(candidate);
            }
        }
        return results;
    }

    private static PoiCandidate toCandidate(Map<String, SparqlResponse.Binding> row) {
        String itemUri = value(row, "item");
        String name = value(row, "itemLabel");
        String coord = value(row, "coord");
        String imageUri = value(row, "image");
        String description = value(row, "desc");

        if (itemUri == null || name == null || coord == null || imageUri == null) {
            return null;
        }

        // Skip rows where the label service produced a bare Q-id rather than a name.
        String externalId = itemUri.substring(itemUri.lastIndexOf('/') + 1);
        if (name.equals(externalId)) {
            return null;
        }

        double[] latLon = parsePoint(coord);
        if (latLon == null) {
            return null;
        }

        String fileName = commonsFileName(imageUri);
        if (fileName == null) {
            return null;
        }

        return new PoiCandidate(externalId, name, description, latLon[0], latLon[1], fileName);
    }

    /** Wikidata returns coordinates as {@code Point(lon lat)} in WKT. */
    private static double[] parsePoint(String wkt) {
        int open = wkt.indexOf('(');
        int close = wkt.indexOf(')');
        if (open < 0 || close < open) {
            return null;
        }
        String[] parts = wkt.substring(open + 1, close).trim().split("\\s+");
        if (parts.length != 2) {
            return null;
        }
        try {
            double lon = Double.parseDouble(parts[0]);
            double lat = Double.parseDouble(parts[1]);
            return new double[]{lat, lon};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Extracts the Commons file name from a {@code Special:FilePath/<file>} URL. */
    private static String commonsFileName(String imageUri) {
        int index = imageUri.indexOf("Special:FilePath/");
        if (index < 0) {
            return null;
        }
        String raw = imageUri.substring(index + "Special:FilePath/".length());
        int query = raw.indexOf('?');
        if (query >= 0) {
            raw = raw.substring(0, query);
        }
        String decoded = URLDecoder.decode(raw, StandardCharsets.UTF_8).trim();
        return decoded.isEmpty() ? null : decoded;
    }

    private static String value(Map<String, SparqlResponse.Binding> row, String key) {
        SparqlResponse.Binding binding = row.get(key);
        if (binding == null || binding.getValue() == null || binding.getValue().isBlank()) {
            return null;
        }
        return binding.getValue();
    }
}
