package org.github.tess1o.geopulse.ai.model;

import org.github.tess1o.geopulse.trips.model.dto.TripDto;
import org.github.tess1o.geopulse.trips.model.dto.TripPlanItemDto;
import org.github.tess1o.geopulse.trips.model.dto.TripSummaryDto;

import java.util.List;

/** A planned trip with its totals and plan items. */
public record AITripDetailsDTO(TripDto trip, TripSummaryDto summary, List<TripPlanItemDto> planItems) {
}
