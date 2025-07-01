package org.github.tess1o.geopulse.service;

import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointPathDTO;
import org.github.tess1o.geopulse.gps.model.GpsPointPathPointDTO;
import org.github.tess1o.geopulse.shared.geo.GpsPoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleValidationTest {

    @Test
    public void testGpsPointEntityImplementsInterface() {
        // Test that GpsPointEntity properly implements GpsPoint interface
        GpsPointEntity entity = new GpsPointEntity();
        assertTrue(entity instanceof GpsPoint);
    }

    @Test
    public void testGpsPointPathPointDTOImplementsInterface() {
        // Test that GpsPointPathPointDTO properly implements GpsPoint interface
        GpsPointPathPointDTO dto = new GpsPointPathPointDTO(
                1L, -74.0, 40.0, Instant.now(), 5.0, 100.0, 10.0, UUID.randomUUID(), "OWNTRACKS"
        );
        assertTrue(dto instanceof GpsPoint);
        assertEquals(40.0, dto.getLatitude());
        assertEquals(-74.0, dto.getLongitude());
    }

    @Test
    public void testGpsPointPathDTOConstruction() {
        // Test basic construction and functionality
        UUID userId = UUID.randomUUID();
        var points = new ArrayList<GpsPointPathPointDTO>();
        points.add(new GpsPointPathPointDTO(
                1L, -74.0, 40.0, Instant.now(), 5.0, 100.0, 10.0, userId, "OWNTRACKS"
        ));
        
        GpsPointPathDTO path = new GpsPointPathDTO(userId, points);
        assertNotNull(path);
        assertEquals(userId, path.getUserId());
        assertEquals(1, path.getPointCount());
        assertEquals(1, path.getPoints().size());
    }
}