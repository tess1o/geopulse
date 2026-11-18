package org.github.tess1o.geopulse.streaming.edits.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.time.Instant;

/**
 * Legacy stored split: the second half of a stay with its own place. New corrections express a split as a
 * {@link PlaceStayPayload} over the second half; this record keeps existing rows and old exports readable.
 */
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SplitStayPayload(
        Instant splitAt,
        String rightLocationName,
        Double rightLatitude,
        Double rightLongitude,
        Long rightFavoriteId,
        Long rightGeocodingId
) implements CorrectionPayload {
}
