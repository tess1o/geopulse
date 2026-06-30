package org.github.tess1o.geopulse.streaming.model.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record BoatSetupStartResponseDTO(
        UUID jobId,
        BoatSetupStatusDTO status
) {
}
