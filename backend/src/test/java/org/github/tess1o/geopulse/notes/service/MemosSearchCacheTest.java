package org.github.tess1o.geopulse.notes.service;

import org.github.tess1o.geopulse.notes.model.NoteDto;
import org.github.tess1o.geopulse.notes.model.NoteSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@Tag("unit")
class MemosSearchCacheTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant START_TIME = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END_TIME = Instant.parse("2026-07-02T00:00:00Z");

    @Test
    void cacheKeyIncludesTagFilters() {
        MemosSearchCache cache = new MemosSearchCache();
        cache.ttlSeconds = 300;
        cache.maxEntries = 10;
        NoteDto included = NoteDto.builder()
                .source(NoteSource.MEMOS)
                .eventTime(START_TIME)
                .build();
        NoteDto excluded = NoteDto.builder()
                .source(NoteSource.MEMOS)
                .eventTime(END_TIME)
                .build();

        cache.put(USER_ID, START_TIME, END_TIME, 100, List.of("travel"), List.of(), List.of(included));
        cache.put(USER_ID, START_TIME, END_TIME, 100, List.of("travel"), List.of("private"), List.of(excluded));

        assertSame(included, cache.get(USER_ID, START_TIME, END_TIME, 100, List.of("travel"), List.of()).getFirst());
        assertSame(excluded, cache.get(USER_ID, START_TIME, END_TIME, 100, List.of("travel"), List.of("private")).getFirst());
        assertNull(cache.get(USER_ID, START_TIME, END_TIME, 100, List.of("work"), List.of()));
    }
}
