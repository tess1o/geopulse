package org.github.tess1o.geopulse.admin.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.dto.AuditLogResponse;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.AuditLogEntity;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.repository.AuditLogRepository;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.shared.api.PageResponse;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.BAD_REQUEST;
import static org.github.tess1o.geopulse.shared.api.ApiProblems.problem;

/**
 * REST resource for admin audit log viewing.
 */
@Path("/admin/audit-logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@Slf4j
@Tag(name = "Admin: Audit Logs", description = "Review administrator audit events and security-relevant activity.")
public class AdminAuditLogResource {

    @Inject
    AuditLogRepository auditLogRepository;

    @Inject
    UserRepository userRepository;

    /**
     * Get paginated list of audit logs with filters.
     */
    @GET
    public PageResponse<AuditLogResponse> getAuditLogs(
            @QueryParam("actionType") String actionTypeStr,
            @QueryParam("targetType") String targetTypeStr,
            @QueryParam("adminUserId") UUID adminUserId,
            @QueryParam("from") Long fromTimestamp,
            @QueryParam("to") Long toTimestamp,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(200) int size) {

        // Parse enum parameters
        ActionType actionType = null;
        if (actionTypeStr != null && !actionTypeStr.isEmpty()) {
            try {
                actionType = ActionType.valueOf(actionTypeStr);
            } catch (IllegalArgumentException e) {
                throw problem(BAD_REQUEST, "Invalid action type", Map.of("actionType", actionTypeStr));
            }
        }

        TargetType targetType = null;
        if (targetTypeStr != null && !targetTypeStr.isEmpty()) {
            try {
                targetType = TargetType.valueOf(targetTypeStr);
            } catch (IllegalArgumentException e) {
                throw problem(BAD_REQUEST, "Invalid target type", Map.of("targetType", targetTypeStr));
            }
        }

        // Parse timestamps
        Instant from = fromTimestamp != null ? Instant.ofEpochMilli(fromTimestamp) : null;
        Instant to = toTimestamp != null ? Instant.ofEpochMilli(toTimestamp) : null;

        // Fetch audit logs
        List<AuditLogEntity> auditLogs = auditLogRepository.findWithFilters(
                actionType, targetType, adminUserId, from, to, page, size);
        long total = auditLogRepository.countWithFilters(
                actionType, targetType, adminUserId, from, to);

        // Fetch admin user emails
        Set<UUID> adminUserIds = auditLogs.stream()
                .map(AuditLogEntity::getAdminUserId)
                .collect(Collectors.toSet());

        Map<UUID, String> adminEmails = new HashMap<>();
        for (UUID userId : adminUserIds) {
            userRepository.findByIdOptional(userId).ifPresent(user ->
                    adminEmails.put(userId, user.getEmail())
            );
        }

        // Map to response DTOs
        List<AuditLogResponse> responses = auditLogs.stream()
                .map(log -> toAuditLogResponse(log, adminEmails.get(log.getAdminUserId())))
                .collect(Collectors.toList());

        return new PageResponse<>(responses, page, size, total, (int) Math.ceil((double) total / size));
    }

    private AuditLogResponse toAuditLogResponse(AuditLogEntity entity, String adminEmail) {
        return AuditLogResponse.builder()
                .id(entity.getId())
                .timestamp(entity.getTimestamp())
                .adminUserId(entity.getAdminUserId())
                .adminEmail(adminEmail != null ? adminEmail : "Unknown")
                .actionType(entity.getActionType())
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .details(entity.getDetails())
                .ipAddress(entity.getIpAddress())
                .build();
    }
}
