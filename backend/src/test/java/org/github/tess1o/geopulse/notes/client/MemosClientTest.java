package org.github.tess1o.geopulse.notes.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.time.Instant;
import java.util.List;

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

    @Test
    void buildMemosFilterCombinesTimeAndIncludeTagsWithAnyTagSemantics() {
        String filter = MemosClient.buildMemosFilter(
                Instant.parse("2026-06-19T21:00:00Z"),
                Instant.parse("2026-06-20T20:59:59.999Z"),
                List.of("travel", "work/project")
        );

        assertEquals(
                "created_ts >= 1781902800 && created_ts <= 1781989199 && (\"travel\" in tags || \"work/project\" in tags)",
                filter
        );
    }

    @Test
    void buildMemosFilterEscapesIncludeTagStringLiterals() {
        String filter = MemosClient.buildMemosFilter(
                null,
                null,
                List.of("quote\"tag", "path\\tag", "line\nbreak")
        );

        assertEquals("(\"quote\\\"tag\" in tags || \"path\\\\tag\" in tags || \"line\\nbreak\" in tags)", filter);
    }

    @Test
    void buildMemosFilterReturnsNullWhenNoFiltersExist() {
        assertNull(MemosClient.buildMemosFilter(null, null, List.of()));
    }
}
