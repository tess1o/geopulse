package org.github.tess1o.geopulse.trips.model.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record TripReconstructionCommitResponseDto(
        UUID groupId,
        long corrections,
        Instant regenerationStartTime,
        String jobId,
        String regenerationWarning
) {
}
