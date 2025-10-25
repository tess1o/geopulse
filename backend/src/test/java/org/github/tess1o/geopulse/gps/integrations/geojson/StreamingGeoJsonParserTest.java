package org.github.tess1o.geopulse.gps.integrations.geojson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.github.tess1o.geopulse.gps.integrations.geojson.model.GeoJsonFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StreamingGeoJsonParser to verify memory-efficient parsing
 */
class StreamingGeoJsonParserTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
    }

    @Test
    void testParseValidGeoJsonWithPoints() throws IOException {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-122.4194, 37.7749]
                  },
                  "properties": {
                    "timestamp": "2024-01-01T12:00:00Z"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-122.4195, 37.7750]
                  },
                  "properties": {
                    "timestamp": "2024-01-01T12:05:00Z"
                  }
                }
              ]
            }
            """;

        StreamingGeoJsonParser parser = new StreamingGeoJsonParser(
                geoJson.getBytes(StandardCharsets.UTF_8), objectMapper);

        List<GeoJsonFeature> parsedFeatures = new ArrayList<>();
        StreamingGeoJsonParser.ParsingStats stats = parser.parseFeatures((feature, currentStats) -> {
            parsedFeatures.add(feature);
        });

        assertEquals(2, stats.totalFeatures);
        assertEquals(2, stats.validFeatures);
        assertEquals(2, stats.totalPoints);
        assertEquals(2, stats.validPoints);
        assertEquals(2, parsedFeatures.size());
    }

    @Test
    void testParseGeoJsonWithLineString() throws IOException {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [-122.4194, 37.7749],
                      [-122.4195, 37.7750],
                      [-122.4196, 37.7751]
                    ]
                  },
                  "properties": {
                    "timestamp": "2024-01-01T12:00:00Z"
                  }
                }
              ]
            }
            """;

        StreamingGeoJsonParser parser = new StreamingGeoJsonParser(
                geoJson.getBytes(StandardCharsets.UTF_8), objectMapper);

        List<GeoJsonFeature> parsedFeatures = new ArrayList<>();
        StreamingGeoJsonParser.ParsingStats stats = parser.parseFeatures((feature, currentStats) -> {
            parsedFeatures.add(feature);
        });

        assertEquals(1, stats.totalFeatures);
        assertEquals(1, stats.validFeatures);
        assertEquals(3, stats.totalPoints);
        assertEquals(3, stats.validPoints);
        assertEquals(1, parsedFeatures.size());
    }

    @Test
    void testParseMixedGeometryTypes() throws IOException {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-122.4194, 37.7749]
                  },
                  "properties": {
                    "timestamp": "2024-01-01T12:00:00Z"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [-122.4195, 37.7750],
                      [-122.4196, 37.7751]
                    ]
                  },
                  "properties": {
                    "timestamp": "2024-01-01T12:05:00Z"
                  }
                }
              ]
            }
            """;

        StreamingGeoJsonParser parser = new StreamingGeoJsonParser(
                geoJson.getBytes(StandardCharsets.UTF_8), objectMapper);

        StreamingGeoJsonParser.ParsingStats stats = parser.parseFeatures((feature, currentStats) -> {
            // No-op
        });

        assertEquals(2, stats.totalFeatures);
        assertEquals(2, stats.validFeatures);
        assertEquals(3, stats.totalPoints); // 1 Point + 2 from LineString
        assertEquals(3, stats.validPoints);
    }

    @Test
    void testParseEmptyFeatureCollection() throws IOException {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": []
            }
            """;

        StreamingGeoJsonParser parser = new StreamingGeoJsonParser(
                geoJson.getBytes(StandardCharsets.UTF_8), objectMapper);

        StreamingGeoJsonParser.ParsingStats stats = parser.parseFeatures((feature, currentStats) -> {
            fail("Should not parse any features");
        });

        assertEquals(0, stats.totalFeatures);
        assertEquals(0, stats.validFeatures);
        assertEquals(0, stats.totalPoints);
        assertEquals(0, stats.validPoints);
    }

    @Test
    void testInvalidGeoJsonType() {
        String geoJson = """
            {
              "type": "Feature",
              "geometry": {
                "type": "Point",
                "coordinates": [-122.4194, 37.7749]
              }
            }
            """;

        StreamingGeoJsonParser parser = new StreamingGeoJsonParser(
                geoJson.getBytes(StandardCharsets.UTF_8), objectMapper);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            parser.parseFeatures((feature, currentStats) -> {});
        });

        assertTrue(exception.getMessage().contains("FeatureCollection"));
    }

    @Test
    void testMissingFeaturesArray() {
        String geoJson = """
            {
              "type": "FeatureCollection"
            }
            """;

        StreamingGeoJsonParser parser = new StreamingGeoJsonParser(
                geoJson.getBytes(StandardCharsets.UTF_8), objectMapper);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            parser.parseFeatures((feature, currentStats) -> {});
        });

        assertTrue(exception.getMessage().contains("features"));
    }

    @Test
    void testInvalidJson() {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature"
            """;

        StreamingGeoJsonParser parser = new StreamingGeoJsonParser(
                geoJson.getBytes(StandardCharsets.UTF_8), objectMapper);

        assertThrows(IOException.class, () -> {
            parser.parseFeatures((feature, currentStats) -> {});
        });
    }

    @Test
    void testCallbackInvokedIncrementally() throws IOException {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-122.4194, 37.7749]
                  },
                  "properties": {
                    "timestamp": "2024-01-01T12:00:00Z"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-122.4195, 37.7750]
                  },
                  "properties": {
                    "timestamp": "2024-01-01T12:05:00Z"
                  }
                }
              ]
            }
            """;

        StreamingGeoJsonParser parser = new StreamingGeoJsonParser(
                geoJson.getBytes(StandardCharsets.UTF_8), objectMapper);

        List<Integer> featureCounts = new ArrayList<>();
        parser.parseFeatures((feature, currentStats) -> {
            // Track cumulative feature count at each callback
            featureCounts.add(currentStats.totalFeatures);
        });

        // Verify callback was invoked incrementally (once per feature)
        assertEquals(2, featureCounts.size());
        assertEquals(1, featureCounts.get(0)); // First callback: 1 feature processed
        assertEquals(2, featureCounts.get(1)); // Second callback: 2 features processed
    }

    @Test
    void testParseGeoJsonWithInvalidCoordinates() throws IOException {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": []
                  },
                  "properties": {
                    "timestamp": "2024-01-01T12:00:00Z"
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [-122.4195, 37.7750]
                  },
                  "properties": {
                    "timestamp": "2024-01-01T12:05:00Z"
                  }
                }
              ]
            }
            """;

        StreamingGeoJsonParser parser = new StreamingGeoJsonParser(
                geoJson.getBytes(StandardCharsets.UTF_8), objectMapper);

        StreamingGeoJsonParser.ParsingStats stats = parser.parseFeatures((feature, currentStats) -> {
            // No-op
        });

        assertEquals(2, stats.totalFeatures);
        assertEquals(1, stats.validFeatures); // Only one has valid coordinates
        assertEquals(1, stats.totalPoints);
        assertEquals(1, stats.validPoints);
    }

    @Test
    void testLargeFeatureCollection() throws IOException {
        // Simulate a large collection with many features
        StringBuilder geoJsonBuilder = new StringBuilder();
        geoJsonBuilder.append("{\"type\":\"FeatureCollection\",\"features\":[");

        int featureCount = 10000;
        for (int i = 0; i < featureCount; i++) {
            if (i > 0) geoJsonBuilder.append(",");
            geoJsonBuilder.append(String.format("""
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [%f, %f]
                  },
                  "properties": {
                    "timestamp": "2024-01-01T12:00:00Z"
                  }
                }
                """, -122.4194 + (i * 0.0001), 37.7749 + (i * 0.0001)));
        }
        geoJsonBuilder.append("]}");

        StreamingGeoJsonParser parser = new StreamingGeoJsonParser(
                geoJsonBuilder.toString().getBytes(StandardCharsets.UTF_8), objectMapper);

        int[] callbackCount = {0};
        StreamingGeoJsonParser.ParsingStats stats = parser.parseFeatures((feature, currentStats) -> {
            callbackCount[0]++;
            // Verify we can process each feature without keeping all in memory
            assertNotNull(feature);
        });

        assertEquals(featureCount, stats.totalFeatures);
        assertEquals(featureCount, stats.validFeatures);
        assertEquals(featureCount, stats.totalPoints);
        assertEquals(featureCount, stats.validPoints);
        assertEquals(featureCount, callbackCount[0]);
    }
}
