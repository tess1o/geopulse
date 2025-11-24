package org.github.tess1o.geopulse.admin.dto;

import lombok.Builder;
import lombok.Data;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.TargetType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for audit log entries, including admin user email.
 */
@Data
@Builder
public class AuditLogResponse {
    private Long id;
    private Instant timestamp;
    private UUID adminUserId;
    private String adminEmail;
    private ActionType actionType;
    private TargetType targetType;
    private String targetId;
    private Map<String, Object> details;
    private String ipAddress;
}
