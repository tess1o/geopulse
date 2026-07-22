package org.github.tess1o.geopulse.notes.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("unit")
class MemosClientTest {

    @Test
    void buildCreatedTimeFilterUsesEpochSeconds() {
        String filter = MemosClient.buildCreatedTimeFilter(
                Instant.parse("2026-06-19T21:00:00Z"),
                Instant.parse("2026-06-20T20:59:59.999Z")
        );

        assertEquals("created_ts >= 1781902800 && created_ts <= 1781989199", filter);
    }

    @Test
    void buildCreatedTimeFilterReturnsNullWhenRangeIncomplete() {
        assertNull(MemosClient.buildCreatedTimeFilter(Instant.EPOCH, null));
        assertNull(MemosClient.buildCreatedTimeFilter(null, Instant.EPOCH));
    }
}
