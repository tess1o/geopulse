package org.github.tess1o.geopulse.poi.client.wikidata;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import org.github.tess1o.geopulse.poi.client.PoiRestClientFactory;
import org.github.tess1o.geopulse.poi.model.PoiCandidate;
import org.github.tess1o.geopulse.poi.service.PoiConfigurationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The JSON below is a real (trimmed) response captured from the Wikidata Query Service,
 * so this pins the Jackson model and the value parsing against the actual payload shape
 * rather than against an assumption about it.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class WikidataPoiClientTest {

    private static final String REAL_RESPONSE = """
            {"head":{"vars":["item","itemLabel","coord","image","desc"]},
             "results":{"bindings":[
               {"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q376896"},
                "coord":{"datatype":"http://www.opengis.net/ont/geosparql#wktLiteral","type":"literal","value":"Point(2.29166667 48.85)"},
                "image":{"type":"uri","value":"http://commons.wikimedia.org/wiki/Special:FilePath/%C3%89glise%20Saint-Jean-Baptiste%20de%20Grenelle%20%28Paris%29%2019.jpg"},
                "desc":{"xml:lang":"en","type":"literal","value":"district of Paris"},
                "itemLabel":{"xml:lang":"en","type":"literal","value":"Grenelle"}},
               {"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q9999"},
                "coord":{"type":"literal","value":"Point(2.30 48.86)"},
                "image":{"type":"uri","value":"http://commons.wikimedia.org/wiki/Special:FilePath/Unlabelled.jpg"},
                "desc":{"type":"literal","value":"unnamed thing"},
                "itemLabel":{"type":"literal","value":"Q9999"}}
             ]}}
            """;

    @Mock
    WikidataSparqlClient sparqlClient;

    @Mock
    PoiRestClientFactory clientFactory;

    @Mock
    PoiConfigurationService config;

    @InjectMocks
    WikidataPoiClient poiClient;

    @Test
    void mapsARealSparqlResponseIntoPoiCandidates() throws Exception {
        SparqlResponse response = new ObjectMapper().readValue(REAL_RESPONSE, SparqlResponse.class);

        when(config.getLanguage()).thenReturn("en");
        when(config.getUserAgent()).thenReturn("GeoPulse/test");
        // The client is built per call from the configured endpoint, so the factory is the
        // seam to stub rather than the client itself.
        when(clientFactory.wikidata()).thenReturn(sparqlClient);
        when(sparqlClient.query(anyString(), anyString(), anyString()))
                .thenReturn(Uni.createFrom().item(response));

        List<PoiCandidate> candidates = poiClient.searchBox(48.85, 48.87, 2.29, 2.31, 3)
                .await().indefinitely();

        // The second row has only a Q-id as its label and must be dropped: rendering it
        // would show "Q9999" as the name of a place.
        assertThat(candidates).hasSize(1);

        PoiCandidate poi = candidates.getFirst();
        assertThat(poi.externalId()).isEqualTo("Q376896");
        assertThat(poi.name()).isEqualTo("Grenelle");
        assertThat(poi.description()).isEqualTo("district of Paris");
        // WKT is Point(lon lat) - latitude must not be taken from the first number.
        assertThat(poi.latitude()).isEqualTo(48.85);
        assertThat(poi.longitude()).isEqualTo(2.29166667);
        // Percent-encoding and the Special:FilePath prefix are both stripped.
        assertThat(poi.imageFile()).isEqualTo("Église Saint-Jean-Baptiste de Grenelle (Paris) 19.jpg");
    }

    @Test
    void reportsProviderFailureRatherThanReturningAnEmptyList() {
        when(config.getLanguage()).thenReturn("en");
        when(config.getUserAgent()).thenReturn("GeoPulse/test");
        when(clientFactory.wikidata()).thenReturn(sparqlClient);
        when(sparqlClient.query(anyString(), anyString(), any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("502 Bad Gateway")));

        // An outage must be distinguishable from "no notable places here".
        assertThat(maybeSearch())
                .isInstanceOf(PoiProviderException.class)
                .hasMessageContaining("Wikidata");
    }

    private Throwable maybeSearch() {
        try {
            poiClient.searchBox(48.85, 48.87, 2.29, 2.31, 3).await().indefinitely();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
