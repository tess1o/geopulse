package org.github.tess1o.geopulse.streaming.iterator;

import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

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

    private List<GPSPoint> currentBuffer;
    private int positionInBuffer;
    private int offset;
    private boolean hasMore;

    public StreamingGpsIterator(
            GpsPointRepository repository,
            UUID userId,
            Instant fromTimestamp,
            int bufferSize) {
        this.repository = repository;
        this.userId = userId;
        this.fromTimestamp = fromTimestamp;
        this.bufferSize = bufferSize;
        this.offset = 0;
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

        return point;
    }

    private void loadNextBuffer() {
        // Load next chunk from database
        currentBuffer = repository.findEssentialDataChunk(
            userId, fromTimestamp, offset, bufferSize);

        positionInBuffer = 0;
        offset += bufferSize;

        // If we got fewer points than buffer size, we've reached the end
        if (currentBuffer.size() < bufferSize) {
            hasMore = false;
        }

        log.debug("Loaded GPS buffer: {} points (offset: {}, hasMore: {})",
                  currentBuffer.size(), offset - bufferSize, hasMore);
    }

    /**
     * Get count of points processed so far (for progress tracking).
     */
    public int getProcessedCount() {
        return offset - bufferSize + positionInBuffer;
    }
}
