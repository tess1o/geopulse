package org.github.tess1o.geopulse.streaming.edits.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

/**
 * A stay inserted over its anchor. {@code explanation}/{@code carveExplanation} carry reason codes such as
 * {@code MANUAL_SPLIT}; {@code reconstruction}/{@code tripId} mark a plan-scoped reconstruction correction.
 */
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaceStayPayload(
        String locationName,
        Double latitude,
        Double longitude,
        Long favoriteId,
        Long geocodingId,
        String visitName,
        Boolean locationNameCustom,
        Boolean resolveLocationName,
        String explanation,
        String carveExplanation,
        Boolean activityCorrection,
        Boolean reconstruction,
        Long tripId
) implements CorrectionPayload {
}
