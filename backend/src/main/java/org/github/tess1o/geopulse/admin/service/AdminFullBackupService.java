package org.github.tess1o.geopulse.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.admin.dto.AdminSettingsBackupDto;
import org.github.tess1o.geopulse.admin.dto.backup.*;
import org.github.tess1o.geopulse.auth.model.UserApiTokenEntity;
import org.github.tess1o.geopulse.auth.oidc.model.UserOidcConnectionEntity;
import org.github.tess1o.geopulse.export.dto.FriendPermissionsDataDto;
import org.github.tess1o.geopulse.export.dto.FriendsDataDto;
import org.github.tess1o.geopulse.export.model.ExportDateRange;
import org.github.tess1o.geopulse.export.model.ExportJob;
import org.github.tess1o.geopulse.export.mapper.ExportDataMapper;
import org.github.tess1o.geopulse.export.service.ExportTempFileService;
import org.github.tess1o.geopulse.export.service.GeoPulseExportService;
import org.github.tess1o.geopulse.friends.model.UserFriendEntity;
import org.github.tess1o.geopulse.friends.model.UserFriendPermissionEntity;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.importdata.model.ImportOptions;
import org.github.tess1o.geopulse.importdata.service.GeoPulseImportStrategy;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.exportimport.SequenceResetService;
import org.github.tess1o.geopulse.user.model.TimelineStatus;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@ApplicationScoped
@Slf4j
public class AdminFullBackupService {
    private static final String BACKUP_PREFIX = "geopulse-full-backup-";
    private static final String BACKUP_SUFFIX = ".zip";
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private static final List<String> PER_USER_DATA_TYPES = List.of(
            ExportImportConstants.DataTypes.RAW_GPS,
            ExportImportConstants.DataTypes.TIMELINE,
            ExportImportConstants.DataTypes.FAVORITES,
            ExportImportConstants.DataTypes.REVERSE_GEOCODING_LOCATION,
            ExportImportConstants.DataTypes.LOCATION_SOURCES,
            ExportImportConstants.DataTypes.USER_INFO,
            ExportImportConstants.DataTypes.PERIOD_TAGS,
            ExportImportConstants.DataTypes.TIMELINE_OVERRIDES,
            ExportImportConstants.DataTypes.TRIP_WORKSPACE,
            ExportImportConstants.DataTypes.NOTIFICATION_TEMPLATES,
            ExportImportConstants.DataTypes.GEOFENCING,
            ExportImportConstants.DataTypes.NOTES,
            ExportImportConstants.DataTypes.WEATHER_SAMPLES,
            ExportImportConstants.DataTypes.MAP_MATCHING
    );

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SystemSettingsService settingsService;

    @Inject
    AdminSettingsBackupService settingsBackupService;

    @Inject
    GeoPulseExportService geoPulseExportService;

    @Inject
    ExportTempFileService exportTempFileService;

    @Inject
    ExportDataMapper exportDataMapper;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    GeoPulseImportStrategy geoPulseImportStrategy;

    @Inject
    SequenceResetService sequenceResetService;

    @Inject
    BackupMaintenanceService maintenanceService;

    @Inject
    AIEncryptionService encryptionService;

    public AdminBackupConfigDto getConfig() {
        return AdminBackupConfigDto.builder()
                .scheduledEnabled(settingsService.getBoolean("backup.scheduled.enabled"))
                .scheduledCron(settingsService.getString("backup.scheduled.cron"))
                .localPath(settingsService.getString("backup.local.path"))
                .retentionCount(settingsService.getInteger("backup.retention.count"))
                .operationTimeoutMinutes(settingsService.getInteger("backup.operation.timeout-minutes"))
                .build();
    }

    @Transactional
    public void updateConfig(AdminBackupConfigDto config, UUID adminId) {
        validateConfig(config);
        settingsService.setValue("backup.scheduled.enabled", Boolean.toString(config.isScheduledEnabled()), adminId);
        settingsService.setValue("backup.scheduled.cron", config.getScheduledCron(), adminId);
        settingsService.setValue("backup.local.path", config.getLocalPath(), adminId);
        settingsService.setValue("backup.retention.count", Integer.toString(config.getRetentionCount()), adminId);
        settingsService.setValue("backup.operation.timeout-minutes", Integer.toString(config.getOperationTimeoutMinutes()), adminId);
    }

    public void validateConfig(AdminBackupConfigDto config) {
        if (config == null) {
            throw new IllegalArgumentException("Backup configuration is required");
        }
        if (config.getScheduledCron() == null || config.getScheduledCron().isBlank()
                || config.getScheduledCron().trim().split("\\s+").length < 5) {
            throw new IllegalArgumentException("A valid cron expression is required");
        }
        if (config.getLocalPath() == null || config.getLocalPath().isBlank()) {
            throw new IllegalArgumentException("Backup folder path is required");
        }
        if (config.getRetentionCount() < 1 || config.getRetentionCount() > 365) {
            throw new IllegalArgumentException("Retention count must be between 1 and 365");
        }
        if (config.getOperationTimeoutMinutes() < 1 || config.getOperationTimeoutMinutes() > 1440) {
            throw new IllegalArgumentException("Operation timeout must be between 1 and 1440 minutes");
        }
        ensureWritableDirectory(Paths.get(config.getLocalPath()));
    }

    public List<AdminBackupFileDto> listLocalBackups() throws IOException {
        Path dir = backupDirectory();
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> isBackupFileName(path.getFileName().toString()))
                    .map(this::toFileDto)
                    .sorted(Comparator.comparing(AdminBackupFileDto::getLastModifiedAt).reversed())
                    .toList();
        }
    }

    public Path resolveLocalBackup(String fileName) {
        if (!isBackupFileName(fileName) || Paths.get(fileName).getNameCount() != 1) {
            throw new IllegalArgumentException("Invalid backup file name");
        }
        Path dir = backupDirectory().toAbsolutePath().normalize();
        Path file = dir.resolve(fileName).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Backup file not found");
        }
        return file;
    }

    public String writeLocalBackup() throws IOException {
        Path dir = backupDirectory();
        ensureWritableDirectory(dir);
        String fileName = BACKUP_PREFIX + FILE_TIMESTAMP.format(Instant.now()) + BACKUP_SUFFIX;
        maintenanceService.updateBackupFile(fileName);
        maintenanceService.updateProgress("preparing", "Preparing local backup file", 0, null, null, null, 2);
        Path tempFile = dir.resolve(fileName + ".tmp");
        Path finalFile = dir.resolve(fileName);
        try (OutputStream os = Files.newOutputStream(tempFile, StandardOpenOption.CREATE_NEW);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            writeFullBackup(zos);
        } catch (Exception e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
        try {
            Files.move(tempFile, finalFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, finalFile, StandardCopyOption.REPLACE_EXISTING);
        }
        rotateBackups();
        return fileName;
    }

    public void deleteLocalBackup(String fileName) throws IOException {
        Path file = resolveLocalBackup(fileName);
        Files.delete(file);
    }

    public void writeFullBackup(ZipOutputStream zos) throws IOException {
        Instant startedAt = Instant.now();
        Instant deadline = operationDeadline();
        List<UserEntity> users = userRepository.listAll();
        maintenanceService.updateProgress("metadata", "Writing backup metadata", 0, users.size(), null, null, 5);
        addJson(zos, "manifest.json", AdminBackupManifestDto.builder()
                .schemaVersion(AdminBackupManifestDto.CURRENT_SCHEMA_VERSION)
                .backupType("geopulse-full")
                .appVersion("unknown")
                .backupStartedAt(startedAt)
                .exportedAt(Instant.now())
                .userCount(users.size())
                .userIds(users.stream().map(UserEntity::getId).toList())
                .warnings(List.of(
                        "Full backups contain plaintext app secrets plus password hashes and API token hashes.",
                        "Runtime JWT private/public keys are not included; restored browser sessions must log in again."
                ))
                .build());
        ensureNotTimedOut(deadline);

        maintenanceService.updateProgress("settings", "Exporting admin settings", 0, users.size(), null, null, 10);
        AdminSettingsBackupDto settingsBackup = settingsBackupService.exportBackup();
        addJson(zos, "admin-settings.json", settingsBackup);
        maintenanceService.updateProgress("users", "Exporting user accounts and credentials", 0, users.size(), null, null, 15);
        addJson(zos, "users/users.json", buildUsersBackup(users));
        maintenanceService.updateProgress("relationships", "Exporting friends and sharing permissions", 0, users.size(), null, null, 20);
        addJson(zos, "relationships/friends.json", exportDataMapper.toFriendsDataDto(listAllFriends()));
        addJson(zos, "relationships/friend-permissions.json", exportDataMapper.toFriendPermissionsDataDto(listAllFriendPermissions()));
        ensureNotTimedOut(deadline);

        for (int i = 0; i < users.size(); i++) {
            UserEntity user = users.get(i);
            ensureNotTimedOut(deadline);
            maintenanceService.updateProgress(
                    "user-data",
                    "Exporting user data for " + user.getEmail(),
                    i,
                    users.size(),
                    user.getId(),
                    user.getEmail(),
                    backupProgressPercent(i, users.size())
            );
            ExportDateRange dateRange = new ExportDateRange();
            dateRange.setStartDate(Instant.EPOCH);
            dateRange.setEndDate(startedAt);
            ExportJob job = new ExportJob(user.getId(), PER_USER_DATA_TYPES, dateRange, "geopulse");
            try {
                geoPulseExportService.generateGeoPulseNativeExport(job);
                Path nestedZip = Paths.get(job.getTempFilePath());
                addFile(zos, "users/" + user.getId() + "/geopulse-export.zip", nestedZip);
            } finally {
                if (job.getTempFilePath() != null) {
                    exportTempFileService.deleteTempFile(job.getTempFilePath());
                }
            }
            maintenanceService.updateProgress(
                    "user-data",
                    "Exported user data for " + user.getEmail(),
                    i + 1,
                    users.size(),
                    user.getId(),
                    user.getEmail(),
                    backupProgressPercent(i + 1, users.size())
            );
        }
        maintenanceService.updateProgress("finalizing", "Finalizing backup archive", users.size(), users.size(), null, null, 98);
    }

    public void restoreFullBackup(byte[] backupBytes, UUID adminId) throws IOException {
        Instant deadline = operationDeadline();
        maintenanceService.updateProgress("reading", "Reading backup archive", null, null, null, null, 5);
        Map<String, byte[]> entries = extractZip(backupBytes, deadline);
        ensureNotTimedOut(deadline);
        maintenanceService.updateProgress("validating", "Validating backup manifest", null, null, null, null, 10);
        AdminBackupManifestDto manifest = objectMapper.readValue(required(entries, "manifest.json"), AdminBackupManifestDto.class);
        if (manifest.getSchemaVersion() != AdminBackupManifestDto.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported full backup schema version: " + manifest.getSchemaVersion());
        }
        AdminFullBackupUsersDto users = objectMapper.readValue(required(entries, "users/users.json"), AdminFullBackupUsersDto.class);
        AdminSettingsBackupDto settings = objectMapper.readValue(required(entries, "admin-settings.json"), AdminSettingsBackupDto.class);
        List<AdminFullBackupUsersDto.UserBackupDto> restoredUsers = nullToList(users.getUsers());

        maintenanceService.updateProgress("settings", "Restoring admin settings", 0, restoredUsers.size(), null, null, 15);
        settingsBackupService.importBackup(settings, adminId);
        ensureNotTimedOut(deadline);
        maintenanceService.updateProgress("users", "Restoring user accounts and credentials", 0, restoredUsers.size(), null, null, 25);
        QuarkusTransaction.requiringNew().run(() -> restoreUsers(users));
        ensureNotTimedOut(deadline);
        maintenanceService.updateProgress("relationships", "Restoring friends and sharing permissions", 0, restoredUsers.size(), null, null, 35);
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                try {
                    replaceRelationships(entries);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }

        for (int i = 0; i < restoredUsers.size(); i++) {
            AdminFullBackupUsersDto.UserBackupDto user = restoredUsers.get(i);
            ensureNotTimedOut(deadline);
            maintenanceService.updateProgress(
                    "user-data",
                    "Restoring user data for " + user.getEmail(),
                    i,
                    restoredUsers.size(),
                    user.getId(),
                    user.getEmail(),
                    restoreProgressPercent(i, restoredUsers.size())
            );
            byte[] nested = entries.get("users/" + user.getId() + "/geopulse-export.zip");
            if (nested == null) {
                continue;
            }
            ImportOptions options = new ImportOptions();
            options.setImportFormat(ExportImportConstants.Formats.GEOPULSE);
            options.setDataTypes(PER_USER_DATA_TYPES);
            options.setClearDataBeforeImport(true);
            options.setSnapshotRestore(true);
            ImportJob job = new ImportJob(user.getId(), options, "geopulse-export.zip", nested);
            geoPulseImportStrategy.processImportData(job);
            maintenanceService.updateProgress(
                    "user-data",
                    "Restored user data for " + user.getEmail(),
                    i + 1,
                    restoredUsers.size(),
                    user.getId(),
                    user.getEmail(),
                    restoreProgressPercent(i + 1, restoredUsers.size())
            );
        }
        maintenanceService.updateProgress("finalizing", "Resetting database sequences", restoredUsers.size(), restoredUsers.size(), null, null, 98);
        sequenceResetService.resetAllSequences();
    }

    void restoreUsers(AdminFullBackupUsersDto usersBackup) {
        for (AdminFullBackupUsersDto.UserBackupDto dto : usersBackup.getUsers()) {
            UserEntity user = userRepository.findById(dto.getId());
            if (user == null) {
                user = userRepository.findByEmailIgnoreCase(dto.getEmail()).orElseGet(UserEntity::new);
                if (user.getId() == null) {
                    user.setId(dto.getId());
                }
            }
            applyUserDto(user, dto);
            userRepository.persist(user);
            replaceUserAuthData(user, dto);
        }
    }

    void replaceRelationships(Map<String, byte[]> entries) throws IOException {
        entityManager.createQuery("DELETE FROM UserFriendPermissionEntity").executeUpdate();
        entityManager.createQuery("DELETE FROM UserFriendEntity").executeUpdate();
        ImportOptions options = new ImportOptions();
        options.setImportFormat(ExportImportConstants.Formats.GEOPULSE);
        options.setDataTypes(List.of(ExportImportConstants.DataTypes.FRIENDS, ExportImportConstants.DataTypes.FRIEND_PERMISSIONS));
        UUID anyUserId = userRepository.listAll().stream().findFirst().map(UserEntity::getId)
                .orElseThrow(() -> new IllegalStateException("No users restored"));
        ImportJob job = new ImportJob(anyUserId, options, "relationships.zip", new byte[0]);
        if (entries.containsKey("relationships/friends.json")) {
            geoPulseImportStrategy.importFriendsData(entries.get("relationships/friends.json"), job);
        }
        if (entries.containsKey("relationships/friend-permissions.json")) {
            geoPulseImportStrategy.importFriendPermissionsData(entries.get("relationships/friend-permissions.json"), job);
        }
    }

    private AdminFullBackupUsersDto buildUsersBackup(List<UserEntity> users) {
        return AdminFullBackupUsersDto.builder()
                .dataType("users")
                .exportDate(Instant.now())
                .users(users.stream().map(this::toUserBackupDto).toList())
                .build();
    }

    private AdminFullBackupUsersDto.UserBackupDto toUserBackupDto(UserEntity user) {
        return AdminFullBackupUsersDto.UserBackupDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .emailVerified(user.isEmailVerified())
                .passwordHash(user.getPasswordHash())
                .fullName(user.getFullName())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .active(user.isActive())
                .role(user.getRole())
                .avatar(user.getAvatar())
                .timezone(user.getTimezone())
                .timelinePreferences(user.getTimelinePreferences())
                .immichPreferences(user.getImmichPreferences())
                .memosPreferences(user.getMemosPreferences())
                .aiSettings(toPortableAiSettings(user))
                .customMapTileUrl(user.getCustomMapTileUrl())
                .customMapStyleUrl(user.getCustomMapStyleUrl())
                .mapRenderMode(user.getMapRenderMode())
                .distanceUnit(user.getDistanceUnit())
                .temperatureUnit(user.getTemperatureUnit())
                .defaultRedirectUrl(user.getDefaultRedirectUrl())
                .dateFormat(user.getDateFormat())
                .timeFormat(user.getTimeFormat())
                .defaultDateRangePreset(user.getDefaultDateRangePreset())
                .coverageEnabled(user.isCoverageEnabled())
                .timelineDisplayPathSimplificationEnabled(user.getTimelineDisplayPathSimplificationEnabled())
                .timelineDisplayPathSimplificationTolerance(user.getTimelineDisplayPathSimplificationTolerance())
                .timelineDisplayPathMaxPoints(user.getTimelineDisplayPathMaxPoints())
                .timelineDisplayPathAdaptiveSimplification(user.getTimelineDisplayPathAdaptiveSimplification())
                .timelineDisplayShowCurrentLocationTelemetry(user.getTimelineDisplayShowCurrentLocationTelemetry())
                .timelineDisplayAutoShowTripReplayControls(user.getTimelineDisplayAutoShowTripReplayControls())
                .timelineDisplayMapMatchingEnabled(user.getTimelineDisplayMapMatchingEnabled())
                .apiTokens(apiTokens(user.getId()))
                .oidcConnections(oidcConnections(user.getId()))
                .build();
    }

    private void applyUserDto(UserEntity user, AdminFullBackupUsersDto.UserBackupDto dto) {
        user.setEmail(dto.getEmail());
        user.setEmailVerified(dto.isEmailVerified());
        user.setPasswordHash(dto.getPasswordHash());
        user.setFullName(dto.getFullName());
        user.setCreatedAt(dto.getCreatedAt());
        user.setUpdatedAt(dto.getUpdatedAt());
        user.setActive(dto.isActive());
        user.setRole(dto.getRole());
        user.setAvatar(dto.getAvatar());
        user.setTimezone(dto.getTimezone() == null ? "UTC" : dto.getTimezone());
        user.setTimelinePreferences(dto.getTimelinePreferences());
        user.setImmichPreferences(dto.getImmichPreferences());
        user.setMemosPreferences(dto.getMemosPreferences());
        user.setTimelineStatus(TimelineStatus.IDLE);
        applyPortableAiSettings(user, dto.getAiSettings());
        user.setCustomMapTileUrl(dto.getCustomMapTileUrl());
        user.setCustomMapStyleUrl(dto.getCustomMapStyleUrl());
        user.setMapRenderMode(dto.getMapRenderMode());
        user.setDistanceUnit(dto.getDistanceUnit());
        user.setTemperatureUnit(dto.getTemperatureUnit());
        user.setDefaultRedirectUrl(dto.getDefaultRedirectUrl());
        user.setDateFormat(dto.getDateFormat());
        user.setTimeFormat(dto.getTimeFormat() == null ? "24h" : dto.getTimeFormat());
        user.setDefaultDateRangePreset(dto.getDefaultDateRangePreset());
        user.setCoverageEnabled(dto.isCoverageEnabled());
        user.setTimelineDisplayPathSimplificationEnabled(dto.getTimelineDisplayPathSimplificationEnabled());
        user.setTimelineDisplayPathSimplificationTolerance(dto.getTimelineDisplayPathSimplificationTolerance());
        user.setTimelineDisplayPathMaxPoints(dto.getTimelineDisplayPathMaxPoints());
        user.setTimelineDisplayPathAdaptiveSimplification(dto.getTimelineDisplayPathAdaptiveSimplification());
        user.setTimelineDisplayShowCurrentLocationTelemetry(dto.getTimelineDisplayShowCurrentLocationTelemetry());
        user.setTimelineDisplayAutoShowTripReplayControls(dto.getTimelineDisplayAutoShowTripReplayControls());
        user.setTimelineDisplayMapMatchingEnabled(dto.getTimelineDisplayMapMatchingEnabled());
    }

    private UserAISettings toPortableAiSettings(UserEntity user) {
        if (user.getAiSettingsEncrypted() == null || user.getAiSettingsEncrypted().isBlank()) {
            return null;
        }
        try {
            String decryptedJson = encryptionService.decrypt(user.getAiSettingsEncrypted(), user.getAiSettingsKeyId());
            UserAISettings settings = objectMapper.readValue(decryptedJson, UserAISettings.class);
            if (settings.getOpenaiApiKey() != null && !settings.getOpenaiApiKey().isBlank()) {
                settings.setOpenaiApiKey(toPortableAiApiKey(user, settings.getOpenaiApiKey()));
                settings.setOpenaiApiKeyConfigured(true);
            } else {
                settings.setOpenaiApiKey("");
                settings.setOpenaiApiKeyConfigured(false);
            }
            return settings;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt AI settings for user " + user.getId(), e);
        }
    }

    private String toPortableAiApiKey(UserEntity user, String storedApiKey) {
        try {
            return encryptionService.decrypt(storedApiKey, user.getAiSettingsKeyId());
        } catch (RuntimeException e) {
            if (isInvalidCiphertext(e)) {
                log.warn("AI settings API key for user {} is stored as non-encrypted text; exporting it as portable plaintext",
                        user.getId());
                return storedApiKey;
            }
            throw e;
        }
    }

    private boolean isInvalidCiphertext(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof IllegalArgumentException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void applyPortableAiSettings(UserEntity user, UserAISettings settings) {
        if (settings == null) {
            user.setAiSettingsEncrypted(null);
            user.setAiSettingsKeyId(null);
            return;
        }
        try {
            UserAISettings settingsToStore = settings.copy();
            if (settings.getOpenaiApiKey() != null && !settings.getOpenaiApiKey().isBlank()) {
                settingsToStore.setOpenaiApiKey(encryptionService.encrypt(settings.getOpenaiApiKey()));
                settingsToStore.setOpenaiApiKeyConfigured(true);
            } else {
                settingsToStore.setOpenaiApiKey("");
                settingsToStore.setOpenaiApiKeyConfigured(false);
            }
            String json = objectMapper.writeValueAsString(settingsToStore);
            user.setAiSettingsEncrypted(encryptionService.encrypt(json));
            user.setAiSettingsKeyId(encryptionService.getCurrentKeyId());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt AI settings for user " + user.getId(), e);
        }
    }

    private void replaceUserAuthData(UserEntity user, AdminFullBackupUsersDto.UserBackupDto dto) {
        entityManager.createQuery("DELETE FROM UserApiTokenEntity token WHERE token.user.id = :userId")
                .setParameter("userId", user.getId()).executeUpdate();
        entityManager.createQuery("DELETE FROM UserOidcConnectionEntity connection WHERE connection.userId = :userId")
                .setParameter("userId", user.getId()).executeUpdate();
        for (AdminFullBackupUsersDto.ApiTokenBackupDto tokenDto : nullToList(dto.getApiTokens())) {
            entityManager.createNativeQuery("""
                            INSERT INTO user_api_tokens (
                                id, user_id, name, token_hash, token_prefix, token_suffix,
                                created_at, expires_at, revoked_at, revoked_by, last_used_at, last_used_ip
                            ) VALUES (
                                :id, :userId, :name, :tokenHash, :tokenPrefix, :tokenSuffix,
                                :createdAt, :expiresAt, :revokedAt, :revokedBy, :lastUsedAt, :lastUsedIp
                            )
                            """)
                    .setParameter("id", tokenDto.getId())
                    .setParameter("userId", user.getId())
                    .setParameter("name", tokenDto.getName())
                    .setParameter("tokenHash", tokenDto.getTokenHash())
                    .setParameter("tokenPrefix", tokenDto.getTokenPrefix())
                    .setParameter("tokenSuffix", tokenDto.getTokenSuffix())
                    .setParameter("createdAt", tokenDto.getCreatedAt())
                    .setParameter("expiresAt", tokenDto.getExpiresAt())
                    .setParameter("revokedAt", tokenDto.getRevokedAt())
                    .setParameter("revokedBy", tokenDto.getRevokedBy())
                    .setParameter("lastUsedAt", tokenDto.getLastUsedAt())
                    .setParameter("lastUsedIp", tokenDto.getLastUsedIp())
                    .executeUpdate();
        }
        for (AdminFullBackupUsersDto.OidcConnectionBackupDto connectionDto : nullToList(dto.getOidcConnections())) {
            entityManager.createNativeQuery("""
                            INSERT INTO user_oidc_connections (
                                user_id, provider_name, external_user_id, display_name, avatar_url, linked_at, last_login_at
                            ) VALUES (
                                :userId, :providerName, :externalUserId, :displayName, :avatarUrl, :linkedAt, :lastLoginAt
                            )
                            """)
                    .setParameter("userId", user.getId())
                    .setParameter("providerName", connectionDto.getProviderName())
                    .setParameter("externalUserId", connectionDto.getExternalUserId())
                    .setParameter("displayName", connectionDto.getDisplayName())
                    .setParameter("avatarUrl", connectionDto.getAvatarUrl())
                    .setParameter("linkedAt", connectionDto.getLinkedAt())
                    .setParameter("lastLoginAt", connectionDto.getLastLoginAt())
                    .executeUpdate();
        }
    }

    private List<AdminFullBackupUsersDto.ApiTokenBackupDto> apiTokens(UUID userId) {
        return entityManager.createQuery("SELECT token FROM UserApiTokenEntity token WHERE token.user.id = :userId", UserApiTokenEntity.class)
                .setParameter("userId", userId)
                .getResultStream()
                .map(token -> AdminFullBackupUsersDto.ApiTokenBackupDto.builder()
                        .id(token.getId()).name(token.getName()).tokenHash(token.getTokenHash())
                        .tokenPrefix(token.getTokenPrefix()).tokenSuffix(token.getTokenSuffix())
                        .createdAt(token.getCreatedAt()).expiresAt(token.getExpiresAt())
                        .revokedAt(token.getRevokedAt()).revokedBy(token.getRevokedBy())
                        .lastUsedAt(token.getLastUsedAt()).lastUsedIp(token.getLastUsedIp())
                        .build())
                .toList();
    }

    private List<AdminFullBackupUsersDto.OidcConnectionBackupDto> oidcConnections(UUID userId) {
        return entityManager.createQuery("SELECT connection FROM UserOidcConnectionEntity connection WHERE connection.userId = :userId", UserOidcConnectionEntity.class)
                .setParameter("userId", userId)
                .getResultStream()
                .map(connection -> AdminFullBackupUsersDto.OidcConnectionBackupDto.builder()
                        .providerName(connection.getProviderName())
                        .externalUserId(connection.getExternalUserId())
                        .displayName(connection.getDisplayName())
                        .avatarUrl(connection.getAvatarUrl())
                        .linkedAt(connection.getLinkedAt())
                        .lastLoginAt(connection.getLastLoginAt())
                        .build())
                .toList();
    }

    private List<UserFriendEntity> listAllFriends() {
        return entityManager.createQuery("SELECT friend FROM UserFriendEntity friend", UserFriendEntity.class).getResultList();
    }

    private List<UserFriendPermissionEntity> listAllFriendPermissions() {
        return entityManager.createQuery("SELECT permission FROM UserFriendPermissionEntity permission", UserFriendPermissionEntity.class).getResultList();
    }

    private void rotateBackups() throws IOException {
        int retention = settingsService.getInteger("backup.retention.count");
        List<AdminBackupFileDto> files = listLocalBackups();
        for (int i = retention; i < files.size(); i++) {
            Files.deleteIfExists(resolveLocalBackup(files.get(i).getFileName()));
        }
    }

    private Path backupDirectory() {
        return Paths.get(settingsService.getString("backup.local.path"));
    }

    private void ensureWritableDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
            if (!Files.isDirectory(dir) || !Files.isWritable(dir)) {
                throw new IllegalArgumentException("Backup folder is not writable: " + dir);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Backup folder is not writable: " + dir, e);
        }
    }

    private boolean isBackupFileName(String fileName) {
        return fileName != null && fileName.startsWith(BACKUP_PREFIX) && fileName.endsWith(BACKUP_SUFFIX);
    }

    private AdminBackupFileDto toFileDto(Path path) {
        try {
            return AdminBackupFileDto.builder()
                    .fileName(path.getFileName().toString())
                    .sizeBytes(Files.size(path))
                    .lastModifiedAt(Files.getLastModifiedTime(path).toInstant())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read backup file metadata: " + path, e);
        }
    }

    private void addJson(ZipOutputStream zos, String name, Object value) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        zos.write(json.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private void addFile(ZipOutputStream zos, String name, Path source) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        Files.copy(source, zos);
        zos.closeEntry();
    }

    private Map<String, byte[]> extractZip(byte[] bytes, Instant deadline) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ensureNotTimedOut(deadline);
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zis.readAllBytes());
                }
                zis.closeEntry();
            }
        }
        return entries;
    }

    private byte[] required(Map<String, byte[]> entries, String name) {
        byte[] content = entries.get(name);
        if (content == null) {
            throw new IllegalArgumentException("Invalid full backup: missing " + name);
        }
        return content;
    }

    private <T> List<T> nullToList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Instant operationDeadline() {
        return Instant.now().plusSeconds(settingsService.getInteger("backup.operation.timeout-minutes") * 60L);
    }

    private void ensureNotTimedOut(Instant deadline) {
        if (Instant.now().isAfter(deadline)) {
            throw new IllegalStateException("Backup operation timed out");
        }
    }

    private int backupProgressPercent(int processedUsers, int totalUsers) {
        if (totalUsers <= 0) {
            return 95;
        }
        return 20 + Math.min(75, (int) Math.round((processedUsers * 75.0) / totalUsers));
    }

    private int restoreProgressPercent(int processedUsers, int totalUsers) {
        if (totalUsers <= 0) {
            return 95;
        }
        return 35 + Math.min(60, (int) Math.round((processedUsers * 60.0) / totalUsers));
    }
}
