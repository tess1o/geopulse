package org.github.tess1o.geopulse.mapmatching.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("unit")
class MapMatchingProfileResolverTest {

    private final MapMatchingProfileResolver resolver = new MapMatchingProfileResolver();

    @Test
    void resolvesOnlyRoadAndPathMovementTypes() {
        assertEquals("pedestrian", resolver.resolveProfile("WALK"));
        assertEquals("pedestrian", resolver.resolveProfile("running"));
        assertEquals("bicycle", resolver.resolveProfile("BICYCLE"));
        assertEquals("auto", resolver.resolveProfile("CAR"));
        assertEquals("auto", resolver.resolveProfile("MOTORCYCLE"));

        assertNull(resolver.resolveProfile("TRAIN"));
        assertNull(resolver.resolveProfile("FLIGHT"));
        assertNull(resolver.resolveProfile("BOAT"));
        assertNull(resolver.resolveProfile("UNKNOWN"));
        assertNull(resolver.resolveProfile(null));
    }
}
