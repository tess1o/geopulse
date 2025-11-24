package org.github.tess1o.geopulse.admin.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class CreateInvitationRequest {
    /**
     * Optional custom expiration time.
     * If not provided, defaults to 7 days from creation.
     */
    private Instant expiresAt;
}
