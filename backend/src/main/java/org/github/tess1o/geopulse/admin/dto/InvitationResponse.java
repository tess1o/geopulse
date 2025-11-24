package org.github.tess1o.geopulse.admin.dto;

import lombok.Builder;
import lombok.Data;
import org.github.tess1o.geopulse.admin.model.InvitationStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class InvitationResponse {
    private UUID id;
    private String token;
    private AdminUserInfo createdBy;
    private Instant createdAt;
    private Instant expiresAt;
    private InvitationStatus status;
    private Instant usedAt;
    private AdminUserInfo usedBy;
    private Instant revokedAt;

    @Data
    @Builder
    public static class AdminUserInfo {
        private UUID id;
        private String email;
        private String fullName;
    }
}
