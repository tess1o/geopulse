package org.github.tess1o.geopulse.streaming.iterator;

import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;

import java.time.Instant;
import java.util.Iterator;
import java.util.UUID;

/**
 * Iterable wrapper for StreamingGpsIterator to enable for-each loops.
 * Enables memory-efficient GPS point streaming: for (GPSPoint p : iterable)
 *
 * This class provides a way to iterate over GPS points without loading them all
 * into memory at once. It creates a StreamingGpsIterator internally that lazily
 * loads data from the database in chunks.
 *
 * Example usage:
 * <pre>
 * StreamingGpsIterable iterable = new StreamingGpsIterable(repository, userId, timestamp);
 * for (GPSPoint point : iterable) {
 *     // Process point - only small buffer kept in memory!
 * }
 * </pre>
 */
public class StreamingGpsIterable implements Iterable<GPSPoint> {

    private final GpsPointRepository repository;
    private final UUID userId;
    private final Instant fromTimestamp;
    private final int bufferSize;
    private final String environmentDatasetVersion;
    /** Exclusive end of the points read, or null to read to the last point. */
    private final Instant toTimestamp;

    // Keep reference to track progress
    private StreamingGpsIterator currentIterator;

    // Cached total count (lazy-loaded on first access)
    private Long cachedTotalCount;

    private static final int DEFAULT_BUFFER_SIZE = 10_000;

    public StreamingGpsIterable(
            GpsPointRepository repository,
            UUID userId,
            Instant fromTimestamp) {
        this(repository, userId, fromTimestamp, DEFAULT_BUFFER_SIZE);
    }

    public StreamingGpsIterable(
            GpsPointRepository repository,
            UUID userId,
            Instant fromTimestamp,
            int bufferSize) {
        this(repository, userId, fromTimestamp, bufferSize, null);
    }

    public StreamingGpsIterable(
            GpsPointRepository repository,
            UUID userId,
            Instant fromTimestamp,
            int bufferSize,
            String environmentDatasetVersion) {
        this(repository, userId, fromTimestamp, null, bufferSize, environmentDatasetVersion);
    }

    /** Reads the points from {@code fromTimestamp} up to, not including, {@code toTimestamp} (null: to the last). */
    public StreamingGpsIterable(
            GpsPointRepository repository,
            UUID userId,
            Instant fromTimestamp,
            Instant toTimestamp,
            int bufferSize,
            String environmentDatasetVersion) {
        this.repository = repository;
        this.userId = userId;
        this.fromTimestamp = fromTimestamp;
        this.toTimestamp = toTimestamp;
        this.bufferSize = bufferSize;
        this.environmentDatasetVersion = environmentDatasetVersion;
    }

    @Override
    public Iterator<GPSPoint> iterator() {
        currentIterator = new StreamingGpsIterator(
                repository, userId, fromTimestamp, toTimestamp, bufferSize, environmentDatasetVersion);
        return currentIterator;
    }

    /**
     * Get the total count of GPS points that will be iterated over.
     * This method performs a count query on the database.
     * The result is cached after the first call.
     *
     * @return total number of GPS points for this user from the specified timestamp
     */
    public Long getTotalCount() {
        if (cachedTotalCount == null) {
            cachedTotalCount = repository.estimatePointCount(userId, fromTimestamp);
        }
        return cachedTotalCount;
    }

}
