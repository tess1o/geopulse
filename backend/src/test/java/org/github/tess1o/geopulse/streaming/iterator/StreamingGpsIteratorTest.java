package org.github.tess1o.geopulse.streaming.iterator;

import org.github.tess1o.geopulse.gps.repository.GpsPointRepository;
import org.github.tess1o.geopulse.streaming.model.domain.GPSPoint;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class StreamingGpsIteratorTest {

    @Test
    void keysetPaginationHandlesDuplicateTimestampsWithoutSkippingOrDuplicatingPoints() {
        Instant firstTimestamp = Instant.parse("2026-07-01T10:00:00Z");
        Instant secondTimestamp = Instant.parse("2026-07-01T10:05:00Z");
        Instant thirdTimestamp = Instant.parse("2026-07-01T10:10:00Z");
        FakeGpsPointRepository repository = new FakeGpsPointRepository(List.of(
                row(1L, firstTimestamp),
                row(2L, firstTimestamp),
                row(3L, secondTimestamp),
                row(4L, secondTimestamp),
                row(5L, thirdTimestamp)
        ));

        StreamingGpsIterator iterator = new StreamingGpsIterator(
                repository,
                UUID.randomUUID(),
                firstTimestamp,
                2
        );

        List<Double> latitudes = new ArrayList<>();
        while (iterator.hasNext()) {
            latitudes.add(iterator.next().getLatitude());
        }

        assertEquals(List.of(1.0, 2.0, 3.0, 4.0, 5.0), latitudes);
        assertEquals(5, iterator.getProcessedCount());
        assertEquals(List.of(
                new CursorRequest(null, null),
                new CursorRequest(firstTimestamp, 2L),
                new CursorRequest(secondTimestamp, 4L)
        ), repository.cursorRequests);
    }

    private static Row row(long id, Instant timestamp) {
        GPSPoint point = new GPSPoint((double) id, 30.0 + id, 1.0, 5.0, timestamp);
        point.setId(id);
        return new Row(id, point);
    }

    private record Row(long id, GPSPoint point) {
    }

    private record CursorRequest(Instant timestamp, Long id) {
    }

    private static class FakeGpsPointRepository extends GpsPointRepository {
        private final List<Row> rows;
        private final List<CursorRequest> cursorRequests = new ArrayList<>();

        private FakeGpsPointRepository(List<Row> rows) {
            this.rows = rows;
        }

        @Override
        public List<GPSPoint> findEssentialDataChunk(UUID userId,
                                                     Instant fromTimestamp,
                                                     Instant cursorTimestamp,
                                                     Long cursorId,
                                                     int limit,
                                                     String environmentDatasetVersion) {
            cursorRequests.add(new CursorRequest(cursorTimestamp, cursorId));

            return rows.stream()
                    .filter(row -> !row.point().getTimestamp().isBefore(fromTimestamp))
                    .filter(row -> isAfterCursor(row, cursorTimestamp, cursorId))
                    .limit(limit)
                    .map(Row::point)
                    .toList();
        }

        private boolean isAfterCursor(Row row, Instant cursorTimestamp, Long cursorId) {
            if (cursorTimestamp == null || cursorId == null) {
                return true;
            }

            Instant rowTimestamp = row.point().getTimestamp();
            return rowTimestamp.isAfter(cursorTimestamp)
                    || (rowTimestamp.equals(cursorTimestamp) && row.id() > cursorId);
        }
    }
}
