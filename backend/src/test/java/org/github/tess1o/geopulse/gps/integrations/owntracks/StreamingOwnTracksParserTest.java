package org.github.tess1o.geopulse.gps.integrations.owntracks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.github.tess1o.geopulse.gps.integrations.owntracks.model.OwnTracksLocationMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class StreamingOwnTracksParserTest {
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Test
    void parsesLegacyArrayFormat() throws IOException {
        String json = """
                [
                  {"_type":"location","lat":50.4501,"lon":30.5234,"tst":1767225600,"tid":"aa"},
                  {"_type":"location","lat":50.4510,"lon":30.5240,"tst":1767225660,"tid":"aa"}
                ]
                """;

        ParseResult result = parse(json);

        assertEquals(2, result.stats.totalMessages);
        assertEquals(2, result.stats.validMessages);
        assertEquals(2, result.messages.size());
        assertEquals(50.4501, result.messages.getFirst().getLat(), 0.000001);
    }

    @Test
    void parsesOcatLocationsWrapper() throws IOException {
        String json = """
                {
                  "count": 2,
                  "locations": [
                    {"_type":"location","lat":50.4501,"lon":30.5234,"tst":1767225600,"topic":"owntracks/x/y"},
                    {"_type":"location","lat":50.4510,"lon":30.5240,"tst":1767225660,"topic":"owntracks/x/y"}
                  ]
                }
                """;

        ParseResult result = parse(json);

        assertEquals(2, result.stats.totalMessages);
        assertEquals(2, result.stats.validMessages);
        assertEquals("owntracks/x/y", result.messages.getFirst().getTopic());
    }

    @Test
    void skipsWrapperMetadataBeforeAndAfterLocations() throws IOException {
        String json = """
                {
                  "meta": {"source": "ocat"},
                  "locations": [
                    {"_type":"location","lat":50.4501,"lon":30.5234,"tst":1767225600},
                    {"_type":"location","lat":null,"lon":30.5240,"tst":1767225660}
                  ],
                  "extra": [{"ignored": true}]
                }
                """;

        ParseResult result = parse(json);

        assertEquals(2, result.stats.totalMessages);
        assertEquals(1, result.stats.validMessages);
        assertEquals(2, result.messages.size());
    }

    @Test
    void rejectsObjectWithoutLocationsArray() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parse("""
                {"count": 0}
                """));

        assertTrue(exception.getMessage().contains("locations array"));
    }

    @Test
    void rejectsNonArrayLocationsField() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parse("""
                {"count": 1, "locations": {"lat": 50.4501, "lon": 30.5234, "tst": 1767225600}}
                """));

        assertTrue(exception.getMessage().contains("'locations' must be an array"));
    }

    private ParseResult parse(String json) throws IOException {
        StreamingOwnTracksParser parser = new StreamingOwnTracksParser(
                json.getBytes(StandardCharsets.UTF_8), objectMapper);
        List<OwnTracksLocationMessage> messages = new ArrayList<>();
        StreamingOwnTracksParser.ParsingStats stats = parser.parseMessages((message, currentStats) -> messages.add(message));
        return new ParseResult(messages, stats);
    }

    private record ParseResult(
            List<OwnTracksLocationMessage> messages,
            StreamingOwnTracksParser.ParsingStats stats) {
    }
}
