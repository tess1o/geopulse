package org.github.tess1o.geopulse.streaming.edits.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

/** The place of an existing stay. */
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SetPlacePayload(
        String locationName,
        Double latitude,
        Double longitude,
        Long favoriteId,
        Long geocodingId
) implements CorrectionPayload {
}
