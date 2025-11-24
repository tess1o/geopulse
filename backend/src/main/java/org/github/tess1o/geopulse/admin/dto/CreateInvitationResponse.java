package org.github.tess1o.geopulse.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CreateInvitationResponse {
    private UUID id;
    private String token;
    private String baseUrl;
    private Instant expiresAt;
}
