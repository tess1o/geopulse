package org.github.tess1o.geopulse.admin.dto;

import lombok.Data;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class CreateInvitationRequest {
    /**
     * Optional custom expiration time.
     * If not provided, defaults to 7 days from creation.
     */
    @Schema(examples = "2025-07-01T00:00:00Z")
    private Instant expiresAt;
}
