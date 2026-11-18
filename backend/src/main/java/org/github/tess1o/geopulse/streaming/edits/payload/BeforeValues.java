package org.github.tess1o.geopulse.streaming.edits.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import org.github.tess1o.geopulse.streaming.model.domain.TimelineEventType;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;

import java.time.Instant;
import java.util.List;

/**
 * Presentation-only snapshot of the event a correction was made against. It is also the storage for dependency
 * sequences ({@code prerequisiteSequences}); it is never used for correctness of the fold.
 */
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record BeforeValues(
        TimelineEventType type,
        Instant start,
        Instant end,
        String visitName,
        String locationName,
        Long favoriteId,
        Long geocodingId,
        Double latitude,
        Double longitude,
        TripType movementType,
        Double startLatitude,
        Double startLongitude,
        Double endLatitude,
        Double endLongitude,
        List<Long> prerequisiteSequences,
        List<Long> cascadeUndoneSequences
) {
}
