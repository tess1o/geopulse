package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record BoatSetupStatusDTO(
        UUID jobId,
        String status,
        String datasetStatus,
        String userEnvironmentStatus,
        String phase,
        int progressPercentage,
        Long downloadedBytes,
        Long totalBytes,
        Long processedGpsPoints,
        Long totalGpsPoints,
        String errorCode,
        String errorMessage,
        String docsUrl,
        String datasetVersion,
        Integer featureCount,
        Instant updatedAt
) {
}
