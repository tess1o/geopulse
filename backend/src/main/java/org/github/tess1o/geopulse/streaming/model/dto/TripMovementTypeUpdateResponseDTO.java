package org.github.tess1o.geopulse.streaming.model.dto;

public record TripMovementTypeUpdateResponseDTO(
        Long tripId,
        String movementType,
        String movementTypeSource,
        String algorithmClassification
) {
}
