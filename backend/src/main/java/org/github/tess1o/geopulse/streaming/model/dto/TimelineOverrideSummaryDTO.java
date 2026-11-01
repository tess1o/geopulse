package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * One manual timeline correction, as kept for re-applying after every regeneration.
 *
 * @param type         MOVEMENT_TYPE, TRIP_SPLIT, DATA_GAP_STAY or STAY_NAME
 * @param id           the correction's id; movement, split and gap corrections can be removed by id, stay names
 *                     follow with the corrections panel
 * @param startTime    start of the trip or data gap that was corrected, or of the stay split out of a trip
 * @param endTime      end of it
 * @param applied      whether the timeline shows the correction now; false when the corrected event no longer comes
 *                     out of generation (its points were deleted or changed)
 * @param movementType the type set, for MOVEMENT_TYPE
 * @param locationName the stay's name, for TRIP_SPLIT, DATA_GAP_STAY (when known) and STAY_NAME
 */
@Builder
public record TimelineOverrideSummaryDTO(
        String type,
        Long id,
        Instant startTime,
        Instant endTime,
        boolean applied,
        String movementType,
        String locationName
) {
}
