package org.github.tess1o.geopulse.admin.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.AuditLogEntity;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.repository.AuditLogRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for logging admin actions to audit trail.
 */
@ApplicationScoped
@Slf4j
public class AuditLogService {

    private final AuditLogRepository repository;

    @Inject
    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Log an admin action.
     */
    @Transactional
    public void logAction(
            UUID adminUserId,
            ActionType actionType,
            TargetType targetType,
            String targetId,
            Map<String, Object> details,
            String ipAddress) {

        AuditLogEntity entry = AuditLogEntity.builder()
                .adminUserId(adminUserId)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .details(details)
                .ipAddress(ipAddress)
                .build();

        repository.persist(entry);

        log.info("Audit: {} performed {} on {} {}",
                adminUserId, actionType, targetType, targetId);
    }

    /**
     * Log a setting change.
     */
    @Transactional
    public void logSettingChange(UUID adminUserId, String settingKey, String oldValue, String newValue, String ipAddress) {
        logAction(
                adminUserId,
                ActionType.SETTING_CHANGED,
                TargetType.SETTING,
                settingKey,
                Map.of("oldValue", oldValue, "newValue", newValue),
                ipAddress
        );
    }

    /**
     * Log a setting reset to default.
     */
    @Transactional
    public void logSettingReset(UUID adminUserId, String settingKey, String oldValue, String ipAddress) {
        logAction(
                adminUserId,
                ActionType.SETTING_RESET,
                TargetType.SETTING,
                settingKey,
                Map.of("oldValue", oldValue),
                ipAddress
        );
    }

    /**
     * Log a user status change.
     */
    @Transactional
    public void logUserStatusChange(UUID adminUserId, UUID targetUserId, boolean enabled, String ipAddress) {
        logAction(
                adminUserId,
                enabled ? ActionType.USER_ENABLED : ActionType.USER_DISABLED,
                TargetType.USER,
                targetUserId.toString(),
                Map.of("enabled", enabled),
                ipAddress
        );
    }

    /**
     * Log a user role change.
     */
    @Transactional
    public void logUserRoleChange(UUID adminUserId, UUID targetUserId, String oldRole, String newRole, String ipAddress) {
        logAction(
                adminUserId,
                ActionType.USER_ROLE_CHANGED,
                TargetType.USER,
                targetUserId.toString(),
                Map.of("oldRole", oldRole, "newRole", newRole),
                ipAddress
        );
    }

    /**
     * Log a user deletion.
     */
    @Transactional
    public void logUserDeleted(UUID adminUserId, UUID targetUserId, String userEmail, String ipAddress) {
        logAction(
                adminUserId,
                ActionType.USER_DELETED,
                TargetType.USER,
                targetUserId.toString(),
                Map.of("email", userEmail),
                ipAddress
        );
    }

    /**
     * Log a password reset by admin.
     */
    @Transactional
    public void logPasswordReset(UUID adminUserId, UUID targetUserId, String ipAddress) {
        logAction(
                adminUserId,
                ActionType.USER_PASSWORD_RESET,
                TargetType.USER,
                targetUserId.toString(),
                Map.of(),
                ipAddress
        );
    }

    /**
     * Get audit logs with filters.
     */
    public List<AuditLogEntity> getAuditLogs(
            ActionType actionType,
            TargetType targetType,
            UUID adminUserId,
            Instant from,
            Instant to,
            int page,
            int size) {

        return repository.findWithFilters(actionType, targetType, adminUserId, from, to, page, size);
    }

    /**
     * Count audit logs with filters.
     */
    public long countAuditLogs(
            ActionType actionType,
            TargetType targetType,
            UUID adminUserId,
            Instant from,
            Instant to) {

        return repository.countWithFilters(actionType, targetType, adminUserId, from, to);
    }
}
