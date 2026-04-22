package org.github.tess1o.geopulse.trips.model.dto;

import lombok.Builder;

import java.time.Instant;

@Builder
public record TripReconstructionCommitResponseDto(
        long generatedPoints,
        long insertedPoints,
        long duplicatePoints,
        Instant regenerationStartTime,
        String jobId,
        String regenerationWarning
) {
}
