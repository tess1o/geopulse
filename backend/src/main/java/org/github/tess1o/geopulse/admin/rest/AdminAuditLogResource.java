package org.github.tess1o.geopulse.admin.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.dto.AuditLogResponse;
import org.github.tess1o.geopulse.admin.dto.PagedResponse;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.AuditLogEntity;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.repository.AuditLogRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST resource for admin audit log viewing.
 */
@Path("/api/admin/audit-logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@Slf4j
public class AdminAuditLogResource {

    @Inject
    AuditLogRepository auditLogRepository;

    @Inject
    UserRepository userRepository;

    /**
     * Get paginated list of audit logs with filters.
     */
    @GET
    public Response getAuditLogs(
            @QueryParam("actionType") String actionTypeStr,
            @QueryParam("targetType") String targetTypeStr,
            @QueryParam("adminUserId") UUID adminUserId,
            @QueryParam("from") Long fromTimestamp,
            @QueryParam("to") Long toTimestamp,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {

        // Parse enum parameters
        ActionType actionType = null;
        if (actionTypeStr != null && !actionTypeStr.isEmpty()) {
            try {
                actionType = ActionType.valueOf(actionTypeStr);
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Invalid action type"))
                        .build();
            }
        }

        TargetType targetType = null;
        if (targetTypeStr != null && !targetTypeStr.isEmpty()) {
            try {
                targetType = TargetType.valueOf(targetTypeStr);
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Invalid target type"))
                        .build();
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

        PagedResponse<AuditLogResponse> response = PagedResponse.<AuditLogResponse>builder()
                .content(responses)
                .totalElements(total)
                .totalPages((int) Math.ceil((double) total / size))
                .page(page)
                .size(size)
                .build();

        return Response.ok(response).build();
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
