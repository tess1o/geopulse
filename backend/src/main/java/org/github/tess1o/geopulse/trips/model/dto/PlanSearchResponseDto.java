package org.github.tess1o.geopulse.trips.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Envelope for trip plan place search.
 *
 * <p>Carries the merged results plus the outcome of the external provider leg, so the
 * client can tell "your search found nothing" apart from "search could not run".
 * Local results (favorites, cached geocoding) are always returned regardless of the
 * external status.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanSearchResponseDto {
    private List<PlanSearchResultDto> results;
    private PlanSearchExternalStatus externalStatus;
    /** Provider that answered, or the one that was attempted and failed. May be null. */
    private String externalProvider;
    /** Human-readable explanation when the status is not OK. Null when OK. */
    private String externalMessage;
}
