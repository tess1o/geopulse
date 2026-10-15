package org.github.tess1o.geopulse.auth.dto;

import lombok.Builder;
import lombok.Data;
import org.github.tess1o.geopulse.auth.model.ApiTokenStatus;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ApiTokenResponse {
    private UUID id;
    private UUID userId;
    private String userEmail;
    private String name;
    private String preview;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant revokedAt;
    private UUID revokedBy;
    private Instant lastUsedAt;
    private String lastUsedIp;
    private ApiTokenStatus status;
    @Schema(description = "What the token may do, e.g. `all` or `read`, `gps:ingest`.")
    private List<String> scopes;
}
