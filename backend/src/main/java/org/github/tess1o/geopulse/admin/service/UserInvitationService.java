package org.github.tess1o.geopulse.admin.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.dto.InvitationResponse;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.InvitationStatus;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.model.UserInvitationEntity;
import org.github.tess1o.geopulse.admin.repository.UserInvitationRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@ApplicationScoped
@Slf4j
public class UserInvitationService {

    @Inject
    UserInvitationRepository invitationRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    AuditLogService auditLogService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int TOKEN_BYTES = 48; // 48 bytes = 64 characters in base64

    /**
     * Generate a cryptographically secure token
     */
    private String generateSecureToken() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return BASE64_ENCODER.encodeToString(randomBytes);
    }

    /**
     * Create a new invitation
     */
    @Transactional
    public UserInvitationEntity createInvitation(UUID adminUserId, Instant expiresAt, String ipAddress) {
        // Default to 7 days from now if no expiry provided
        if (expiresAt == null) {
            expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        }

        // Validate that expiry is in the future
        if (expiresAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Expiration date must be in the future");
        }

        String token = generateSecureToken();

        UserInvitationEntity invitation = UserInvitationEntity.builder()
                .token(token)
                .createdBy(adminUserId)
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .used(false)
                .revoked(false)
                .build();

        invitationRepository.persist(invitation);

        // Log to audit
        Map<String, Object> details = new HashMap<>();
        details.put("invitationId", invitation.getId().toString());
        details.put("expiresAt", expiresAt.toString());
        auditLogService.logAction(
                adminUserId,
                ActionType.INVITATION_CREATED,
                TargetType.INVITATION,
                invitation.getId().toString(),
                details,
                ipAddress
        );

        log.info("Created invitation {} by admin {}, expires at {}", invitation.getId(), adminUserId, expiresAt);

        return invitation;
    }

    /**
     * Get all invitations with optional status filter
     */
    public List<InvitationResponse> getInvitations(InvitationStatus status, int page, int size) {
        List<UserInvitationEntity> invitations;

        if (status != null) {
            invitations = invitationRepository.findByStatus(status, page, size);
        } else {
            invitations = invitationRepository.findAllPaginated(page, size);
        }

        return invitations.stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Count invitations by status
     */
    public long countInvitations(InvitationStatus status) {
        if (status != null) {
            return invitationRepository.countByStatus(status);
        }
        return invitationRepository.countAll();
    }

    /**
     * Get invitation by ID
     */
    public Optional<UserInvitationEntity> getInvitationById(UUID id) {
        return invitationRepository.findByIdOptional(id);
    }

    /**
     * Revoke an invitation
     */
    @Transactional
    public void revokeInvitation(UUID invitationId, UUID adminUserId, String ipAddress) {
        UserInvitationEntity invitation = invitationRepository.findByIdOptional(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found"));

        if (invitation.isUsed()) {
            throw new IllegalArgumentException("Cannot revoke an already used invitation");
        }

        if (invitation.isRevoked()) {
            throw new IllegalArgumentException("Invitation is already revoked");
        }

        invitation.setRevoked(true);
        invitation.setRevokedAt(Instant.now());
        invitationRepository.persist(invitation);

        // Log to audit
        Map<String, Object> details = new HashMap<>();
        details.put("invitationId", invitationId.toString());
        details.put("token", invitation.getToken().substring(0, 8) + "...");
        auditLogService.logAction(
                adminUserId,
                ActionType.INVITATION_REVOKED,
                TargetType.INVITATION,
                invitationId.toString(),
                details,
                ipAddress
        );

        log.info("Revoked invitation {} by admin {}", invitationId, adminUserId);
    }

    /**
     * Validate an invitation token
     */
    public UserInvitationEntity validateToken(String token) {
        return invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation token"));
    }

    /**
     * Mark an invitation as used
     */
    @Transactional
    public void markAsUsed(String token, UUID userId) {
        UserInvitationEntity invitation = validateToken(token);

        if (invitation.isUsed()) {
            throw new IllegalArgumentException("Invitation has already been used");
        }

        if (invitation.isRevoked()) {
            throw new IllegalArgumentException("Invitation has been revoked");
        }

        if (invitation.isExpired()) {
            throw new IllegalArgumentException("Invitation has expired");
        }

        invitation.setUsed(true);
        invitation.setUsedAt(Instant.now());
        invitation.setUsedBy(userId);
        invitationRepository.persist(invitation);

        log.info("Marked invitation {} as used by user {}", invitation.getId(), userId);
    }

    /**
     * Convert entity to response DTO
     */
    private InvitationResponse toResponse(UserInvitationEntity entity) {
        InvitationResponse.InvitationResponseBuilder builder = InvitationResponse.builder()
                .id(entity.getId())
                .token(entity.getToken())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .status(entity.getStatus())
                .usedAt(entity.getUsedAt())
                .revokedAt(entity.getRevokedAt());

        // Load creator info
        UserEntity creator = userRepository.findById(entity.getCreatedBy());
        if (creator != null) {
            builder.createdBy(InvitationResponse.AdminUserInfo.builder()
                    .id(creator.getId())
                    .email(creator.getEmail())
                    .fullName(creator.getFullName())
                    .build());
        }

        // Load user info if used
        if (entity.getUsedBy() != null) {
            UserEntity user = userRepository.findById(entity.getUsedBy());
            if (user != null) {
                builder.usedBy(InvitationResponse.AdminUserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .build());
            }
        }

        return builder.build();
    }
}
