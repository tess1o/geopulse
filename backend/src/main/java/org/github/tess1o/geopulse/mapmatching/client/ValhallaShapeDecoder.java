package org.github.tess1o.geopulse.mapmatching.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes Valhalla's encoded shapes (Google polyline encoding at 6-digit precision).
 * Shared by map matching ({@code /trace_route}) and plan routing ({@code /route}).
 */
public final class ValhallaShapeDecoder {

    private ValhallaShapeDecoder() {
    }

    /** @return the shape's points as {@code [longitude, latitude]} pairs */
    public static List<List<Double>> decode(String encoded) {
        List<List<Double>> coordinates = new ArrayList<>();
        if (encoded == null) {
            return coordinates;
        }

        int index = 0;
        int lat = 0;
        int lon = 0;

        while (index < encoded.length()) {
            DecodeResult latResult = decodeValue(encoded, index);
            index = latResult.nextIndex();
            lat += latResult.value();

            DecodeResult lonResult = decodeValue(encoded, index);
            index = lonResult.nextIndex();
            lon += lonResult.value();

            coordinates.add(List.of(lon / 1_000_000.0, lat / 1_000_000.0));
        }

        return coordinates;
    }

    private static DecodeResult decodeValue(String encoded, int startIndex) {
        int result = 1;
        int shift = 0;
        int index = startIndex;
        int value;

        do {
            value = encoded.charAt(index++) - 63 - 1;
            result += value << shift;
            shift += 5;
        } while (value >= 0x1f && index < encoded.length());

        return new DecodeResult((result & 1) != 0 ? ~(result >> 1) : result >> 1, index);
    }

    private record DecodeResult(int value, int nextIndex) {
    }
}
