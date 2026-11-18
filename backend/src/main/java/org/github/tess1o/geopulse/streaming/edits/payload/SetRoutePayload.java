package org.github.tess1o.geopulse.streaming.edits.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto;

import java.util.List;

/** Replacement route geometry for an existing trip. */
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SetRoutePayload(List<TripReconstructionWaypointDto> waypoints) implements CorrectionPayload {
}
