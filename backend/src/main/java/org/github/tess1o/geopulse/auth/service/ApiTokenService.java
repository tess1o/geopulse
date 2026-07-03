package org.github.tess1o.geopulse.auth.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.auth.dto.ApiTokenResponse;
import org.github.tess1o.geopulse.auth.dto.CreateApiTokenResponse;
import org.github.tess1o.geopulse.auth.model.ApiTokenAuthenticationResult;
import org.github.tess1o.geopulse.auth.model.ApiTokenStatus;
import org.github.tess1o.geopulse.auth.model.UserApiTokenEntity;
import org.github.tess1o.geopulse.auth.repository.UserApiTokenRepository;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class ApiTokenService {
    private static final Duration LAST_USED_UPDATE_INTERVAL = Duration.ofMinutes(5);

    @Inject
    UserApiTokenRepository apiTokenRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    AuditLogService auditLogService;

    @Inject
    ApiTokenSecretService apiTokenSecretService;

    @Transactional
    public CreateApiTokenResponse createToken(UUID userId, String name, Instant expiresAt, String ipAddress) {
        UserEntity user = findUserOrThrow(userId);
        validateExpiresAt(expiresAt);

        String rawToken = apiTokenSecretService.generateToken();
        UserApiTokenEntity entity = UserApiTokenEntity.builder()
                .user(user)
                .name(normalizeName(name))
                .tokenHash(apiTokenSecretService.hashToken(rawToken))
                .tokenPrefix(ApiTokenSecretService.TOKEN_PREFIX)
                .tokenSuffix(apiTokenSecretService.suffix(rawToken))
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .build();

        apiTokenRepository.persist(entity);

        auditLogService.logAction(
                userId,
                ActionType.API_TOKEN_CREATED,
                TargetType.API_TOKEN,
                entity.getId().toString(),
                auditDetails(entity),
                ipAddress
        );

        return CreateApiTokenResponse.builder()
                .apiToken(toResponse(entity))
                .token(rawToken)
                .build();
    }

    public List<ApiTokenResponse> listForUser(UUID userId) {
        return apiTokenRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ApiTokenResponse> listForAdmin(UUID userId, ApiTokenStatus status, int page, int size) {
        return apiTokenRepository.findForAdmin(userId, status, page, size).stream()
                .map(this::toResponse)
                .toList();
    }

    public long countForAdmin(UUID userId, ApiTokenStatus status) {
        return apiTokenRepository.countForAdmin(userId, status);
    }

    @Transactional
    public ApiTokenResponse updateToken(UUID userId, UUID tokenId, String name, Instant expiresAt, String ipAddress) {
        UserApiTokenEntity entity = findOwnedToken(userId, tokenId);
        if (entity.isRevoked()) {
            throw new IllegalArgumentException("Cannot update a revoked token");
        }
        validateExpiresAt(expiresAt);

        entity.setName(normalizeName(name));
        entity.setExpiresAt(expiresAt);
        apiTokenRepository.persist(entity);

        auditLogService.logAction(
                userId,
                ActionType.API_TOKEN_UPDATED,
                TargetType.API_TOKEN,
                entity.getId().toString(),
                auditDetails(entity),
                ipAddress
        );

        return toResponse(entity);
    }

    @Transactional
    public void revokeOwnedToken(UUID userId, UUID tokenId, String ipAddress) {
        UserApiTokenEntity entity = findOwnedToken(userId, tokenId);
        revoke(entity, userId, ActionType.API_TOKEN_REVOKED, ipAddress);
    }

    @Transactional
    public void revokeTokenAsAdmin(UUID adminUserId, UUID tokenId, String ipAddress) {
        UserApiTokenEntity entity = apiTokenRepository.findByIdOptional(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("API token not found"));
        revoke(entity, adminUserId, ActionType.API_TOKEN_ADMIN_REVOKED, ipAddress);
    }

    @Transactional
    public Optional<ApiTokenAuthenticationResult> authenticate(String rawToken) {
        if (!apiTokenSecretService.hasTokenPrefix(rawToken)) {
            return Optional.empty();
        }

        Optional<UserApiTokenEntity> tokenOpt = apiTokenRepository.findByHash(apiTokenSecretService.hashToken(rawToken));
        if (tokenOpt.isEmpty()) {
            return Optional.empty();
        }

        UserApiTokenEntity token = tokenOpt.get();
        UserEntity user = token.getUser();
        if (user == null || !user.isActive() || !token.isActive()) {
            return Optional.empty();
        }

        return Optional.of(new ApiTokenAuthenticationResult(token, user));
    }

    @Transactional
    public void recordUsage(UUID tokenId, String ipAddress) {
        UserApiTokenEntity token = apiTokenRepository.findById(tokenId);
        if (token == null) {
            return;
        }

        Instant now = Instant.now();
        if (token.getLastUsedAt() != null
                && token.getLastUsedAt().isAfter(now.minus(LAST_USED_UPDATE_INTERVAL))
                && Objects.equals(token.getLastUsedIp(), ipAddress)) {
            return;
        }

        token.setLastUsedAt(now);
        token.setLastUsedIp(ipAddress);
        apiTokenRepository.persist(token);
    }

    public ApiTokenResponse toResponse(UserApiTokenEntity entity) {
        UserEntity user = entity.getUser();
        return ApiTokenResponse.builder()
                .id(entity.getId())
                .userId(user != null ? user.getId() : null)
                .userEmail(user != null ? user.getEmail() : null)
                .name(entity.getName())
                .preview(entity.getPreview())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .revokedAt(entity.getRevokedAt())
                .revokedBy(entity.getRevokedBy())
                .lastUsedAt(entity.getLastUsedAt())
                .lastUsedIp(entity.getLastUsedIp())
                .status(entity.getStatus())
                .build();
    }

    private UserEntity findUserOrThrow(UUID userId) {
        UserEntity user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        return user;
    }

    private UserApiTokenEntity findOwnedToken(UUID userId, UUID tokenId) {
        UserApiTokenEntity entity = apiTokenRepository.findByIdOptional(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("API token not found"));
        if (entity.getUser() == null || !userId.equals(entity.getUser().getId())) {
            throw new IllegalArgumentException("API token not found");
        }
        return entity;
    }

    private void revoke(UserApiTokenEntity entity, UUID revokedBy, ActionType actionType, String ipAddress) {
        if (entity.isRevoked()) {
            throw new IllegalArgumentException("API token is already revoked");
        }

        entity.setRevokedAt(Instant.now());
        entity.setRevokedBy(revokedBy);
        apiTokenRepository.persist(entity);

        auditLogService.logAction(
                revokedBy,
                actionType,
                TargetType.API_TOKEN,
                entity.getId().toString(),
                auditDetails(entity),
                ipAddress
        );
    }

    private void validateExpiresAt(Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Expiration date must be in the future");
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "API token";
        }
        return name.trim();
    }

    private Map<String, Object> auditDetails(UserApiTokenEntity entity) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("tokenId", entity.getId().toString());
        details.put("userId", entity.getUser().getId().toString());
        details.put("name", entity.getName());
        details.put("preview", entity.getPreview());
        details.put("expiresAt", entity.getExpiresAt() != null ? entity.getExpiresAt().toString() : null);
        details.put("status", entity.getStatus().name());
        return details;
    }
}
