package org.github.tess1o.geopulse.notes.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.github.tess1o.geopulse.notes.model.NoteDto;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class MemosSearchCache {

    private final ConcurrentHashMap<NoteSearchCacheKey, CachedMemosSearchResult> cache = new ConcurrentHashMap<>();

    @ConfigProperty(name = "geopulse.memos.notes.search-cache-ttl-seconds", defaultValue = "300")
    long ttlSeconds;

    @ConfigProperty(name = "geopulse.memos.notes.search-cache-max-entries", defaultValue = "200")
    int maxEntries;

    List<NoteDto> get(UUID userId, Instant startTime, Instant endTime, int limit) {
        evictExpiredEntries();
        long nowEpochMillis = Instant.now().toEpochMilli();
        CachedMemosSearchResult cached = cache.computeIfPresent(
                new NoteSearchCacheKey(userId, startTime, endTime, limit),
                (ignored, value) -> value.expiresAtEpochMillis() <= nowEpochMillis
                        ? null
                        : value.touch(nowEpochMillis)
        );
        return cached != null ? cached.notes() : null;
    }

    void put(UUID userId, Instant startTime, Instant endTime, int limit, List<NoteDto> notes) {
        long nowEpochMillis = Instant.now().toEpochMilli();
        long expiresAtEpochMillis = nowEpochMillis + Math.max(1L, ttlSeconds) * 1000L;
        cache.put(
                new NoteSearchCacheKey(userId, startTime, endTime, limit),
                new CachedMemosSearchResult(List.copyOf(notes), expiresAtEpochMillis, nowEpochMillis)
        );
        evictExpiredEntries();
        evictEntriesForSizeLimit();
    }

    void invalidateForUser(UUID userId) {
        cache.entrySet().removeIf(entry -> entry.getKey().userId().equals(userId));
    }

    private void evictExpiredEntries() {
        long nowEpochMillis = Instant.now().toEpochMilli();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochMillis() <= nowEpochMillis);
    }

    private void evictEntriesForSizeLimit() {
        int safeMaxEntries = Math.max(1, maxEntries);
        int overflow = cache.size() - safeMaxEntries;
        if (overflow <= 0) {
            return;
        }
        cache.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().lastAccessEpochMillis()))
                .limit(overflow)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(cache::remove);
    }

    private record NoteSearchCacheKey(UUID userId, Instant startTime, Instant endTime, int limit) {
    }

    private record CachedMemosSearchResult(List<NoteDto> notes, long expiresAtEpochMillis, long lastAccessEpochMillis) {
        private CachedMemosSearchResult touch(long touchedAtEpochMillis) {
            return new CachedMemosSearchResult(notes, expiresAtEpochMillis, touchedAtEpochMillis);
        }
    }
}
