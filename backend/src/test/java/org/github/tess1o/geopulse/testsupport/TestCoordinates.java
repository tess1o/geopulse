package org.github.tess1o.geopulse.testsupport;

import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.locationtech.jts.geom.Point;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class TestCoordinates {
    private static final AtomicLong SCOPE_COUNTER = new AtomicLong(0);
    private static final long SESSION_SALT = UUID.randomUUID().getMostSignificantBits()
            ^ UUID.randomUUID().getLeastSignificantBits();

    private TestCoordinates() {
    }

    public static Scope newScope() {
        long index = SCOPE_COUNTER.incrementAndGet();
        long mixedLon = mix64(SESSION_SALT ^ (index * 0x9E3779B97F4A7C15L));
        long mixedLat = mix64((SESSION_SALT << 1) ^ (index * 0xC2B2AE3D27D4EB4FL));
        double uniqueStep = index / 1_000_000_000.0;

        double lonOffset = toOffset(mixedLon) + uniqueStep;
        double latOffset = toOffset(mixedLat) + uniqueStep;
        return new Scope(lonOffset, latOffset);
    }

    private static double toOffset(long mixed) {
        double normalized = (mixed & Long.MAX_VALUE) / (double) Long.MAX_VALUE;
        return 0.000001 + (normalized * 0.001);
    }

    private static long mix64(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public static final class Scope {
        private final double lonOffset;
        private final double latOffset;

        private Scope(double lonOffset, double latOffset) {
            this.lonOffset = lonOffset;
            this.latOffset = latOffset;
        }

        public Point point(double lon, double lat) {
            return GeoUtils.createPoint(lon + lonOffset, lat + latOffset);
        }
    }
}
