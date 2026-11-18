package org.github.tess1o.geopulse.streaming.edits.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import org.github.tess1o.geopulse.streaming.model.shared.TripType;
import org.github.tess1o.geopulse.trips.model.dto.TripReconstructionWaypointDto;

import java.util.List;

/** A trip inserted over its anchor, optionally with a manual route. */
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaceTripPayload(
        Long startFavoriteId,
        Long startGeocodingId,
        Double startLatitude,
        Double startLongitude,
        String startLocationName,
        Long endFavoriteId,
        Long endGeocodingId,
        Double endLatitude,
        Double endLongitude,
        String endLocationName,
        TripType movementType,
        Long distanceMeters,
        List<TripReconstructionWaypointDto> waypoints,
        Boolean activityCorrection,
        Boolean reconstruction,
        Long tripId
) implements CorrectionPayload {
}
