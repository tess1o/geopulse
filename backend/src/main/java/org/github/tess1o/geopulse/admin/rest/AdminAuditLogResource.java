package org.github.tess1o.geopulse.admin.rest;

import org.github.tess1o.geopulse.shared.api.GeoPulseException;

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
import org.github.tess1o.geopulse.shared.openapi.ApiTags;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.BAD_REQUEST;

/**
 * REST resource for admin audit log viewing.
 */
@Path("/admin/audit-logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@Slf4j
@Tag(name = ApiTags.ADMIN_AUDIT_LOGS)
public class AdminAuditLogResource {

    @Inject
    AuditLogRepository auditLogRepository;

    @Inject
    UserRepository userRepository;

    /**
     * Get paginated list of audit logs with filters.
     */
    @GET
    @Operation(summary = "List audit log entries",
            description = "Returns administrator actions and security events, newest first, one page at a time, with "
                    + "filters. Each entry has the acting administrator, action, target, details, and IP address.")
    public PageResponse<AuditLogResponse> getAuditLogs(
            @Parameter(description = "Only entries with this action type, such as `USER_ROLE_CHANGED`.")
            @QueryParam("actionType") String actionTypeStr,
            @Parameter(description = "Only entries about this kind of target: `SETTING`, `USER`, `OIDC_PROVIDER`, "
                    + "`INVITATION`, `API_TOKEN`, `TIMELINE_REGENERATION_CAMPAIGN`, or `BACKUP`.", example = "USER")
            @QueryParam("targetType") String targetTypeStr,
            @Parameter(description = "Only entries for actions by this administrator.")
            @QueryParam("adminUserId") UUID adminUserId,
            @Parameter(description = "Only entries at or after this time, in epoch milliseconds.",
                    example = "1735689600000")
            @QueryParam("from") Long fromTimestamp,
            @Parameter(description = "Only entries at or before this time, in epoch milliseconds.",
                    example = "1767225599000")
            @QueryParam("to") Long toTimestamp,
            @Parameter(description = "Page number, starting at 0.")
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @Parameter(description = "Page size, from 1 to 200. Defaults to 20.")
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(200) int size) {

        // Parse enum parameters
        ActionType actionType = null;
        if (actionTypeStr != null && !actionTypeStr.isEmpty()) {
            try {
                actionType = ActionType.valueOf(actionTypeStr);
            } catch (IllegalArgumentException e) {
                throw new GeoPulseException(BAD_REQUEST, "Invalid action type", Map.of("actionType", actionTypeStr), e);
            }
        }

        TargetType targetType = null;
        if (targetTypeStr != null && !targetTypeStr.isEmpty()) {
            try {
                targetType = TargetType.valueOf(targetTypeStr);
            } catch (IllegalArgumentException e) {
                throw new GeoPulseException(BAD_REQUEST, "Invalid target type", Map.of("targetType", targetTypeStr), e);
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
