package org.github.tess1o.geopulse.streaming.iterator;

import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Iterator that lazily loads GPS points from database in chunks to prevent OOM.
 * Memory footprint: O(BUFFER_SIZE) instead of O(total_points).
 *
 * This iterator maintains a small buffer of GPS points and loads the next batch
 * from the database when the current buffer is exhausted. This allows processing
 * millions of GPS points with constant memory usage.
 */
@Slf4j
public class StreamingGpsIterator implements Iterator<GPSPoint> {

    private final GpsPointRepository repository;
    private final UUID userId;
    private final Instant fromTimestamp;
    private final int bufferSize;
    private final String environmentDatasetVersion;

    private List<GPSPoint> currentBuffer;
    private int positionInBuffer;
    private Instant cursorTimestamp;
    private Long cursorId;
    private int processedCount;
    private boolean hasMore;

    public StreamingGpsIterator(
            GpsPointRepository repository,
            UUID userId,
            Instant fromTimestamp,
            int bufferSize) {
        this(repository, userId, fromTimestamp, bufferSize, null);
    }

    public StreamingGpsIterator(
            GpsPointRepository repository,
            UUID userId,
            Instant fromTimestamp,
            int bufferSize,
            String environmentDatasetVersion) {
        this.repository = repository;
        this.userId = userId;
        this.fromTimestamp = fromTimestamp;
        this.bufferSize = bufferSize;
        this.environmentDatasetVersion = environmentDatasetVersion;
        this.cursorTimestamp = null;
        this.cursorId = null;
        this.processedCount = 0;
        this.positionInBuffer = 0;
        this.hasMore = true;

        // Load first buffer
        loadNextBuffer();
    }

    @Override
    public boolean hasNext() {
        // If we have points in current buffer, return true
        if (currentBuffer != null && positionInBuffer < currentBuffer.size()) {
            return true;
        }

        // If current buffer exhausted, try to load next buffer
        if (hasMore && currentBuffer != null && currentBuffer.size() == bufferSize) {
            loadNextBuffer();
            return currentBuffer != null && !currentBuffer.isEmpty();
        }

        return false;
    }

    @Override
    public GPSPoint next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more GPS points available");
        }

        GPSPoint point = currentBuffer.get(positionInBuffer);
        positionInBuffer++;
        processedCount++;

        return point;
    }

    private void loadNextBuffer() {
        Instant previousCursorTimestamp = cursorTimestamp;
        Long previousCursorId = cursorId;
        long loadStartNanos = System.nanoTime();

        currentBuffer = repository.findEssentialDataChunk(
                userId,
                fromTimestamp,
                cursorTimestamp,
                cursorId,
                bufferSize,
                environmentDatasetVersion
        );

        positionInBuffer = 0;

        if (!currentBuffer.isEmpty()) {
            GPSPoint lastPoint = currentBuffer.getLast();
            cursorTimestamp = lastPoint.getTimestamp();
            cursorId = lastPoint.getId();
        }

        if (currentBuffer.size() < bufferSize) {
            hasMore = false;
        }

        log.debug("Loaded GPS stream chunk for user {}: {} points in {} ms " +
                        "(fromTimestamp={}, cursorTimestamp={}, cursorId={}, nextCursorTimestamp={}, nextCursorId={}, hasMore={})",
                userId,
                currentBuffer.size(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - loadStartNanos),
                fromTimestamp,
                previousCursorTimestamp,
                previousCursorId,
                cursorTimestamp,
                cursorId,
                hasMore);
    }

    /**
     * Get count of points processed so far (for progress tracking).
     */
    public int getProcessedCount() {
        return processedCount;
    }
}
