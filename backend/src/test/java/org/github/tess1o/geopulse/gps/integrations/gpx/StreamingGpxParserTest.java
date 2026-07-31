package org.github.tess1o.geopulse.gps.integrations.gpx;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class StreamingGpxParserTest {

    @Test
    void skipsInvalidTrackPointWithNestedChildrenAndParsesFollowingSibling() throws IOException {
        String gpx = """
                <gpx version="1.1" creator="GeoPulseTest">
                    <trk>
                        <trkseg>
                            <trkpt lat="not-a-number" lon="25.5965">
                                <ele>100.0</ele>
                                <extensions>
                                    <nested>
                                        <value>ignored</value>
                                    </nested>
                                </extensions>
                                <time>2026-01-01T00:00:00Z</time>
                            </trkpt>
                            <trkpt lat="49.5480" lon="25.5970">
                                <ele>101.0</ele>
                                <time>2026-01-01T00:01:00Z</time>
                            </trkpt>
                        </trkseg>
                    </trk>
                </gpx>
                """;

        List<StreamingGpxParser.GpxPoint> points = parse(gpx);

        assertEquals(1, points.size());
        StreamingGpxParser.GpxPoint point = points.getFirst();
        assertEquals("trackpoint", point.type);
        assertEquals(49.5480, point.lat, 0.000001);
        assertEquals(25.5970, point.lon, 0.000001);
        assertEquals(Instant.parse("2026-01-01T00:01:00Z"), point.time);
    }

    @Test
    void skipsInvalidWaypointWithNestedChildrenAndParsesFollowingSibling() throws IOException {
        String gpx = """
                <gpx version="1.1" creator="GeoPulseTest">
                    <wpt lat="49.5473">
                        <name>Invalid waypoint</name>
                        <extensions>
                            <nested>
                                <value>ignored</value>
                            </nested>
                        </extensions>
                        <time>2026-01-01T00:00:00Z</time>
                    </wpt>
                    <wpt lat="49.5480" lon="25.5970">
                        <name>Valid waypoint</name>
                        <ele>101.0</ele>
                        <time>2026-01-01T00:01:00Z</time>
                    </wpt>
                </gpx>
                """;

        List<StreamingGpxParser.GpxPoint> points = parse(gpx);

        assertEquals(1, points.size());
        StreamingGpxParser.GpxPoint point = points.getFirst();
        assertEquals("waypoint", point.type);
        assertEquals(49.5480, point.lat, 0.000001);
        assertEquals(25.5970, point.lon, 0.000001);
        assertEquals(Instant.parse("2026-01-01T00:01:00Z"), point.time);
    }

    private List<StreamingGpxParser.GpxPoint> parse(String gpx) throws IOException {
        StreamingGpxParser parser = new StreamingGpxParser(
                new ByteArrayInputStream(gpx.getBytes(StandardCharsets.UTF_8)));
        List<StreamingGpxParser.GpxPoint> points = new ArrayList<>();
        StreamingGpxParser.ParsingStats stats = parser.parseGpsPoints((point, currentStats) -> points.add(point));

        assertEquals(points.size(), stats.totalGpsPoints);
        return points;
    }
}
