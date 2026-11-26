package org.github.tess1o.geopulse.admin.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.AuditLogEntity;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.repository.AuditLogRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for logging admin actions to audit trail.
 */
@ApplicationScoped
@Slf4j
public class AuditLogService {

    private final AuditLogRepository repository;
    private final UserRepository userRepository;

    @Inject
    public AuditLogService(AuditLogRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
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
                // Kept with the entry, so it still names the admin after the admin's account is deleted.
                .adminEmail(adminUserId == null ? null
                        : userRepository.findByIdOptional(adminUserId).map(UserEntity::getEmail).orElse(null))
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
    public void logSettingChange(UUID adminUserId, String settingKey, String oldValue, String newValue,
                                 boolean redactValues, String ipAddress) {
        logAction(
                adminUserId,
                ActionType.SETTING_CHANGED,
                TargetType.SETTING,
                settingKey,
                redactValues ? Map.of("redacted", true) : Map.of("oldValue", oldValue, "newValue", newValue),
                ipAddress
        );
    }

    /**
     * Log a setting reset to default.
     */
    @Transactional
    public void logSettingReset(UUID adminUserId, String settingKey, String oldValue,
                                boolean redactValues, String ipAddress) {
        logAction(
                adminUserId,
                ActionType.SETTING_RESET,
                TargetType.SETTING,
                settingKey,
                redactValues ? Map.of("redacted", true) : Map.of("oldValue", oldValue),
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
                new LinkedHashMap<>(Map.of("email", userEmail)),
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
}
