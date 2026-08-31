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
import org.github.tess1o.geopulse.admin.model.Role;
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
import org.github.tess1o.geopulse.shared.map.MapRenderMode;
import org.github.tess1o.geopulse.user.model.DistanceUnit;
import org.github.tess1o.geopulse.user.model.TemperatureUnit;
import org.github.tess1o.geopulse.user.model.TimelineStatus;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.hibernate.Session;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.SQLException;
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
    private static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;
    private static final int REFERENCE_DATASET_SCHEMA_VERSION = 2;
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
    private static final String REFERENCE_GEONAMES_COUNTRY = "reference/geonames-country.csv";
    private static final String REFERENCE_GEONAMES_CITY = "reference/geonames-city.csv";
    private static final String REFERENCE_GEO_DATASET_METADATA = "reference/geo-dataset-metadata.csv";
    private static final String REFERENCE_WATER_SURFACE_POLYGONS = "reference/water-surface-polygons.csv";
    private static final String REFERENCE_WATER_DATASET_STATE = "reference/water-dataset-state.csv";
    private static final List<String> REFERENCE_DATASET_ENTRIES = List.of(
            REFERENCE_GEONAMES_COUNTRY,
            REFERENCE_GEONAMES_CITY,
            REFERENCE_GEO_DATASET_METADATA,
            REFERENCE_WATER_SURFACE_POLYGONS,
            REFERENCE_WATER_DATASET_STATE
    );
    private static final List<String> GEONAMES_COUNTRY_COLUMNS = List.of(
            "iso_alpha2", "iso_alpha3", "iso_numeric", "fips_code", "country_name", "capital",
            "area_sq_km", "population", "continent", "tld", "currency_code", "currency_name",
            "phone", "postal_code_format", "postal_code_regex", "languages", "geonameid",
            "neighbors", "equivalent_fips_code"
    );
    private static final List<String> GEONAMES_CITY_COLUMNS = List.of(
            "geonameid", "name", "asciiname", "alternatenames", "latitude", "longitude",
            "feature_class", "feature_code", "country_code", "cc2", "admin1_code", "admin2_code",
            "admin3_code", "admin4_code", "population", "elevation", "dem", "timezone", "modification_date"
    );
    private static final List<String> GEO_DATASET_METADATA_COLUMNS = List.of(
            "dataset_name", "source_url", "source_version", "license", "attribution", "feature_count", "imported_at"
    );
    private static final List<String> WATER_DATASET_STATE_COLUMNS = List.of(
            "dataset_key", "status", "phase", "progress_percentage", "downloaded_bytes", "total_bytes",
            "artifact_url", "local_path", "sha256", "dataset_version", "feature_count", "error_code",
            "error_message", "started_at", "completed_at", "updated_at"
    );
    private static final String GEONAMES_COUNTRY_COLUMN_LIST = String.join(", ", GEONAMES_COUNTRY_COLUMNS);
    private static final String GEONAMES_CITY_COLUMN_LIST = String.join(", ", GEONAMES_CITY_COLUMNS);
    private static final String GEO_DATASET_METADATA_COLUMN_LIST = String.join(", ", GEO_DATASET_METADATA_COLUMNS);
    private static final String WATER_DATASET_STATE_COLUMN_LIST = String.join(", ", WATER_DATASET_STATE_COLUMNS);

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
                .referenceEntries(REFERENCE_DATASET_ENTRIES)
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

        maintenanceService.updateProgress("reference-data", "Exporting reference datasets", 0, users.size(), null, null, 25);
        exportReferenceDatasets(zos);
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
        try (BackupArchive archive = extractZip(backupBytes, deadline)) {
            ensureNotTimedOut(deadline);
            maintenanceService.updateProgress("validating", "Validating backup archive", null, null, null, null, 10);
            AdminBackupManifestDto manifest = objectMapper.readValue(archive.readRequired("manifest.json"), AdminBackupManifestDto.class);
            AdminFullBackupUsersDto users = objectMapper.readValue(archive.readRequired("users/users.json"), AdminFullBackupUsersDto.class);
            AdminSettingsBackupDto settings = objectMapper.readValue(archive.readRequired("admin-settings.json"), AdminSettingsBackupDto.class);
            validateFullBackup(manifest, users, settings, archive);

            boolean restoreReferenceDatasets = includesReferenceDatasets(manifest);
            List<AdminFullBackupUsersDto.UserBackupDto> restoredUsers = nullToList(users.getUsers());
            maintenanceService.markRestoreDataMutationStarted();
            maintenanceService.updateProgress("clearing", "Clearing existing application data", 0, restoredUsers.size(), null, null, 12);
            QuarkusTransaction.requiringNew().run(() -> truncateRestorableState(restoreReferenceDatasets));

            if (restoreReferenceDatasets) {
                ensureNotTimedOut(deadline);
                maintenanceService.updateProgress("reference-data", "Restoring reference datasets", 0, restoredUsers.size(), null, null, 20);
                QuarkusTransaction.requiringNew().run(() -> restoreReferenceDatasets(archive));
            }

            ensureNotTimedOut(deadline);
            maintenanceService.updateProgress("users", "Restoring user accounts", 0, restoredUsers.size(), null, null, 28);
            Set<UUID> restoredUserIds = QuarkusTransaction.requiringNew().call(() -> restoreUsers(users));

            ensureNotTimedOut(deadline);
            maintenanceService.updateProgress("settings", "Restoring admin settings", 0, restoredUsers.size(), null, null, 34);
            QuarkusTransaction.requiringNew().run(() ->
                    settingsBackupService.importBackup(settings, existingUserIdOrNull(adminId)));

            ensureNotTimedOut(deadline);
            maintenanceService.updateProgress("relationships", "Restoring friends and sharing permissions", 0, restoredUsers.size(), null, null, 38);
            runInNewTransaction(() -> replaceRelationships(archive));

            for (int i = 0; i < restoredUsers.size(); i++) {
                AdminFullBackupUsersDto.UserBackupDto user = restoredUsers.get(i);
                UUID targetUserId = user.getId();
                if (!restoredUserIds.contains(targetUserId)) {
                    throw new IllegalStateException("No restored target user found for backup user " + targetUserId);
                }
                ensureNotTimedOut(deadline);
                maintenanceService.updateProgress(
                        "user-data",
                        "Restoring user data for " + user.getEmail(),
                        i,
                        restoredUsers.size(),
                        targetUserId,
                        user.getEmail(),
                        restoreProgressPercent(i, restoredUsers.size())
                );
                Path nested = archive.entryPath("users/" + user.getId() + "/geopulse-export.zip").orElse(null);
                if (nested == null) {
                    continue;
                }
                ImportOptions options = new ImportOptions();
                options.setImportFormat(ExportImportConstants.Formats.GEOPULSE);
                options.setDataTypes(PER_USER_DATA_TYPES);
                options.setClearDataBeforeImport(true);
                options.setSnapshotRestore(true);
                ImportJob job = new ImportJob(targetUserId, options, "geopulse-export.zip", Files.readAllBytes(nested));
                geoPulseImportStrategy.processImportData(job);
                maintenanceService.updateProgress(
                        "user-data",
                        "Restored user data for " + user.getEmail(),
                        i + 1,
                        restoredUsers.size(),
                        targetUserId,
                        user.getEmail(),
                        restoreProgressPercent(i + 1, restoredUsers.size())
                );
            }
            maintenanceService.updateProgress("finalizing", "Resetting database sequences", restoredUsers.size(), restoredUsers.size(), null, null, 98);
            QuarkusTransaction.requiringNew().run(() -> {
                sequenceResetService.resetAllSequences();
                resetWaterSurfaceSequence();
            });
        }
    }

    Set<UUID> restoreUsers(AdminFullBackupUsersDto usersBackup) {
        Set<UUID> restoredUserIds = new LinkedHashSet<>();
        List<AdminFullBackupUsersDto.UserBackupDto> users = nullToList(usersBackup.getUsers());

        for (AdminFullBackupUsersDto.UserBackupDto dto : users) {
            UUID backupUserId = requireBackupUserId(dto);
            UserEntity user = new UserEntity();
            user.setId(backupUserId);
            applyUserDto(user, dto);

            insertUser(user);
            restoredUserIds.add(backupUserId);
            entityManager.flush();
            entityManager.clear();
        }

        for (AdminFullBackupUsersDto.UserBackupDto dto : users) {
            restoreUserAuthData(requireBackupUserId(dto), dto);
        }

        return restoredUserIds;
    }

    private void replaceRelationships(BackupArchive archive) throws IOException {
        entityManager.createNativeQuery("DELETE FROM user_friend_permissions").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM user_friends").executeUpdate();
        entityManager.flush();
        entityManager.clear();
        if (archive.hasEntry("relationships/friends.json")) {
            restoreFriendships(archive.readRequired("relationships/friends.json"));
        }
        if (archive.hasEntry("relationships/friend-permissions.json")) {
            restoreFriendPermissions(archive.readRequired("relationships/friend-permissions.json"));
        }
        entityManager.flush();
        entityManager.clear();
    }

    private void restoreFriendships(byte[] content) throws IOException {
        FriendsDataDto data = objectMapper.readValue(content, FriendsDataDto.class);
        Set<String> restored = new HashSet<>();
        for (FriendsDataDto.FriendDto dto : nullToList(data.getFriends())) {
            UUID userId = resolveRestoredUserId(dto.getUserId(), dto.getUserEmail()).orElse(null);
            UUID friendId = resolveRestoredUserId(dto.getFriendId(), dto.getFriendEmail()).orElse(null);
            if (userId == null || friendId == null || userId.equals(friendId)) {
                continue;
            }
            String key = userId + "\n" + friendId;
            if (!restored.add(key)) {
                continue;
            }
            entityManager.createNativeQuery("""
                            INSERT INTO user_friends (user_id, friend_id, created_at)
                            VALUES (:userId, :friendId, :createdAt)
                            ON CONFLICT (user_id, friend_id) DO UPDATE SET
                                created_at = EXCLUDED.created_at
                            """)
                    .setParameter("userId", userId)
                    .setParameter("friendId", friendId)
                    .setParameter("createdAt", defaultInstant(dto.getCreatedAt()))
                    .executeUpdate();
        }
    }

    private void restoreFriendPermissions(byte[] content) throws IOException {
        FriendPermissionsDataDto data = objectMapper.readValue(content, FriendPermissionsDataDto.class);
        for (FriendPermissionsDataDto.FriendPermissionDto dto : nullToList(data.getPermissions())) {
            UUID userId = resolveRestoredUserId(dto.getUserId(), dto.getUserEmail()).orElse(null);
            UUID friendId = resolveRestoredUserId(dto.getFriendId(), dto.getFriendEmail()).orElse(null);
            if (userId == null || friendId == null || userId.equals(friendId)) {
                continue;
            }
            entityManager.createNativeQuery("""
                            INSERT INTO user_friend_permissions (
                                user_id, friend_id, share_timeline, share_live_location, created_at, updated_at
                            ) VALUES (
                                :userId, :friendId, :shareTimeline, :shareLiveLocation, :createdAt, :updatedAt
                            )
                            ON CONFLICT (user_id, friend_id) DO UPDATE SET
                                share_timeline = EXCLUDED.share_timeline,
                                share_live_location = EXCLUDED.share_live_location,
                                updated_at = EXCLUDED.updated_at
                            """)
                    .setParameter("userId", userId)
                    .setParameter("friendId", friendId)
                    .setParameter("shareTimeline", Boolean.TRUE.equals(dto.getShareTimeline()))
                    .setParameter("shareLiveLocation", Boolean.TRUE.equals(dto.getShareLiveLocation()))
                    .setParameter("createdAt", defaultInstant(dto.getCreatedAt()))
                    .setParameter("updatedAt", defaultInstant(dto.getUpdatedAt()))
                    .executeUpdate();
        }
    }

    private Optional<UUID> resolveRestoredUserId(UUID userId, String email) {
        if (userId != null) {
            UUID restoredUserId = (UUID) entityManager.createNativeQuery("""
                            SELECT id
                            FROM users
                            WHERE id = :userId
                            """)
                    .setParameter("userId", userId)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
            if (restoredUserId != null) {
                return Optional.of(restoredUserId);
            }
        }
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return entityManager.createNativeQuery("""
                        SELECT id
                        FROM users
                        WHERE LOWER(email) = :email
                        """)
                .setParameter("email", email.trim().toLowerCase(Locale.ROOT))
                .getResultStream()
                .findFirst()
                .map(value -> (UUID) value);
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
        user.setRole(dto.getRole() == null ? Role.USER : dto.getRole());
        user.setAvatar(dto.getAvatar());
        user.setTimezone(dto.getTimezone() == null ? "UTC" : dto.getTimezone());
        user.setTimelinePreferences(dto.getTimelinePreferences());
        user.setImmichPreferences(dto.getImmichPreferences());
        user.setMemosPreferences(dto.getMemosPreferences());
        user.setTimelineStatus(TimelineStatus.IDLE);
        applyPortableAiSettings(user, dto.getAiSettings());
        user.setCustomMapTileUrl(dto.getCustomMapTileUrl());
        user.setCustomMapStyleUrl(dto.getCustomMapStyleUrl());
        user.setMapRenderMode(dto.getMapRenderMode() == null ? MapRenderMode.VECTOR : dto.getMapRenderMode());
        user.setDistanceUnit(dto.getDistanceUnit() == null ? DistanceUnit.KILOMETERS : dto.getDistanceUnit());
        user.setTemperatureUnit(dto.getTemperatureUnit() == null ? TemperatureUnit.CELSIUS : dto.getTemperatureUnit());
        user.setDefaultRedirectUrl(dto.getDefaultRedirectUrl());
        user.setDateFormat(dto.getDateFormat());
        user.setTimeFormat(dto.getTimeFormat() == null ? "24h" : dto.getTimeFormat());
        user.setDefaultDateRangePreset(dto.getDefaultDateRangePreset());
        user.setCoverageEnabled(dto.isCoverageEnabled());
        user.setTimelineDisplayPathSimplificationEnabled(dto.getTimelineDisplayPathSimplificationEnabled());
        user.setTimelineDisplayPathSimplificationTolerance(dto.getTimelineDisplayPathSimplificationTolerance());
        user.setTimelineDisplayPathMaxPoints(dto.getTimelineDisplayPathMaxPoints());
        user.setTimelineDisplayPathAdaptiveSimplification(dto.getTimelineDisplayPathAdaptiveSimplification());
        user.setTimelineDisplayShowCurrentLocationTelemetry(
                dto.getTimelineDisplayShowCurrentLocationTelemetry() == null
                        ? Boolean.TRUE
                        : dto.getTimelineDisplayShowCurrentLocationTelemetry());
        user.setTimelineDisplayAutoShowTripReplayControls(
                dto.getTimelineDisplayAutoShowTripReplayControls() == null
                        ? Boolean.TRUE
                        : dto.getTimelineDisplayAutoShowTripReplayControls());
        user.setTimelineDisplayMapMatchingEnabled(
                dto.getTimelineDisplayMapMatchingEnabled() == null
                        ? Boolean.FALSE
                        : dto.getTimelineDisplayMapMatchingEnabled());
    }

    private UUID requireBackupUserId(AdminFullBackupUsersDto.UserBackupDto dto) {
        if (dto.getId() == null) {
            throw new IllegalArgumentException("Invalid full backup: user id is required for " + dto.getEmail());
        }
        return dto.getId();
    }

    private void restoreBackupTimestamps(UUID userId, AdminFullBackupUsersDto.UserBackupDto dto) {
        if (dto.getCreatedAt() != null) {
            entityManager.createNativeQuery("UPDATE users SET created_at = :createdAt WHERE id = :userId")
                    .setParameter("createdAt", dto.getCreatedAt())
                    .setParameter("userId", userId)
                    .executeUpdate();
        }
        if (dto.getUpdatedAt() != null) {
            entityManager.createNativeQuery("UPDATE users SET updated_at = :updatedAt WHERE id = :userId")
                    .setParameter("updatedAt", dto.getUpdatedAt())
                    .setParameter("userId", userId)
                    .executeUpdate();
        }
    }

    private void insertUser(UserEntity user) {
        entityManager.createNativeQuery("""
                        INSERT INTO users (
                            id,
                            email,
                            emailVerified,
                            password_hash,
                            full_name,
                            created_at,
                            updated_at,
                            is_active,
                            role,
                            avatar,
                            timezone,
                            timeline_preferences,
                            immich_preferences,
                            memos_preferences,
                            timeline_status,
                            ai_settings_encrypted,
                            ai_settings_key_id,
                            custom_map_tile_url,
                            custom_map_style_url,
                            map_render_mode,
                            distance_unit,
                            temperature_unit,
                            default_redirect_url,
                            date_format,
                            time_format,
                            default_date_range_preset,
                            coverage_enabled,
                            timeline_display_path_simplification_enabled,
                            timeline_display_path_simplification_tolerance,
                            timeline_display_path_max_points,
                            timeline_display_path_adaptive_simplification,
                            timeline_display_show_current_location_telemetry,
                            timeline_display_auto_show_trip_replay_controls,
                            timeline_display_map_matching_enabled
                        ) VALUES (
                            :id,
                            :email,
                            :emailVerified,
                            :passwordHash,
                            :fullName,
                            :createdAt,
                            :updatedAt,
                            :active,
                            :role,
                            :avatar,
                            :timezone,
                            CAST(:timelinePreferences AS jsonb),
                            CAST(:immichPreferences AS jsonb),
                            CAST(:memosPreferences AS jsonb),
                            :timelineStatus,
                            :aiSettingsEncrypted,
                            :aiSettingsKeyId,
                            :customMapTileUrl,
                            :customMapStyleUrl,
                            :mapRenderMode,
                            :distanceUnit,
                            :temperatureUnit,
                            :defaultRedirectUrl,
                            :dateFormat,
                            :timeFormat,
                            :defaultDateRangePreset,
                            :coverageEnabled,
                            :timelineDisplayPathSimplificationEnabled,
                            :timelineDisplayPathSimplificationTolerance,
                            :timelineDisplayPathMaxPoints,
                            :timelineDisplayPathAdaptiveSimplification,
                            :timelineDisplayShowCurrentLocationTelemetry,
                            :timelineDisplayAutoShowTripReplayControls,
                            :timelineDisplayMapMatchingEnabled
                        )
                        """)
                .setParameter("id", user.getId())
                .setParameter("email", user.getEmail())
                .setParameter("emailVerified", user.isEmailVerified())
                .setParameter("passwordHash", user.getPasswordHash())
                .setParameter("fullName", user.getFullName())
                .setParameter("createdAt", defaultInstant(user.getCreatedAt()))
                .setParameter("updatedAt", defaultInstant(user.getUpdatedAt()))
                .setParameter("active", user.isActive())
                .setParameter("role", user.getRole().name())
                .setParameter("avatar", user.getAvatar())
                .setParameter("timezone", user.getTimezone())
                .setParameter("timelinePreferences", jsonOrNull(user.getTimelinePreferences()))
                .setParameter("immichPreferences", jsonOrNull(user.getImmichPreferences()))
                .setParameter("memosPreferences", jsonOrNull(user.getMemosPreferences()))
                .setParameter("timelineStatus", user.getTimelineStatus().name())
                .setParameter("aiSettingsEncrypted", user.getAiSettingsEncrypted())
                .setParameter("aiSettingsKeyId", user.getAiSettingsKeyId())
                .setParameter("customMapTileUrl", user.getCustomMapTileUrl())
                .setParameter("customMapStyleUrl", user.getCustomMapStyleUrl())
                .setParameter("mapRenderMode", user.getMapRenderMode().name())
                .setParameter("distanceUnit", user.getDistanceUnit().name())
                .setParameter("temperatureUnit", user.getTemperatureUnit().name())
                .setParameter("defaultRedirectUrl", user.getDefaultRedirectUrl())
                .setParameter("dateFormat", user.getDateFormat())
                .setParameter("timeFormat", user.getTimeFormat())
                .setParameter("defaultDateRangePreset", user.getDefaultDateRangePreset())
                .setParameter("coverageEnabled", user.isCoverageEnabled())
                .setParameter("timelineDisplayPathSimplificationEnabled", user.getTimelineDisplayPathSimplificationEnabled())
                .setParameter("timelineDisplayPathSimplificationTolerance", user.getTimelineDisplayPathSimplificationTolerance())
                .setParameter("timelineDisplayPathMaxPoints", user.getTimelineDisplayPathMaxPoints())
                .setParameter("timelineDisplayPathAdaptiveSimplification", user.getTimelineDisplayPathAdaptiveSimplification())
                .setParameter("timelineDisplayShowCurrentLocationTelemetry", user.getTimelineDisplayShowCurrentLocationTelemetry())
                .setParameter("timelineDisplayAutoShowTripReplayControls", user.getTimelineDisplayAutoShowTripReplayControls())
                .setParameter("timelineDisplayMapMatchingEnabled", user.getTimelineDisplayMapMatchingEnabled())
                .executeUpdate();
    }

    private String jsonOrNull(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize restored user preference JSON", e);
        }
    }

    private void validateFullBackup(AdminBackupManifestDto manifest,
                                    AdminFullBackupUsersDto users,
                                    AdminSettingsBackupDto settings,
                                    BackupArchive archive) {
        validateManifest(manifest);
        validateUsersBackup(manifest, users);
        settingsBackupService.validateBackupForImport(settings);
        if (includesReferenceDatasets(manifest)) {
            if (manifest.getReferenceEntries() == null
                    || !new HashSet<>(manifest.getReferenceEntries()).containsAll(REFERENCE_DATASET_ENTRIES)) {
                throw new IllegalArgumentException("Invalid full backup: manifest is missing reference dataset entries");
            }
            for (String entry : REFERENCE_DATASET_ENTRIES) {
                archive.requiredPath(entry);
            }
        }
    }

    private void validateManifest(AdminBackupManifestDto manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("Invalid full backup: manifest is missing");
        }
        if (!"geopulse-full".equals(manifest.getBackupType())) {
            throw new IllegalArgumentException("Invalid full backup type: " + manifest.getBackupType());
        }
        if (manifest.getSchemaVersion() < MIN_SUPPORTED_SCHEMA_VERSION
                || manifest.getSchemaVersion() > AdminBackupManifestDto.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported full backup schema version: " + manifest.getSchemaVersion());
        }
    }

    private void validateUsersBackup(AdminBackupManifestDto manifest, AdminFullBackupUsersDto usersBackup) {
        if (usersBackup == null) {
            throw new IllegalArgumentException("Invalid full backup: users backup is missing");
        }
        List<AdminFullBackupUsersDto.UserBackupDto> users = nullToList(usersBackup.getUsers());
        if (manifest.getUserCount() != users.size()) {
            throw new IllegalArgumentException("Invalid full backup: manifest user count does not match users backup");
        }

        Set<UUID> userIds = new LinkedHashSet<>();
        Set<String> emails = new HashSet<>();
        for (AdminFullBackupUsersDto.UserBackupDto user : users) {
            if (user == null) {
                throw new IllegalArgumentException("Invalid full backup: users backup contains an empty user");
            }
            UUID userId = requireBackupUserId(user);
            if (!userIds.add(userId)) {
                throw new IllegalArgumentException("Invalid full backup: duplicate user id " + userId);
            }
            requireText(user.getEmail(), "Invalid full backup: user email is required for " + userId);
            String normalizedEmail = user.getEmail().trim().toLowerCase(Locale.ROOT);
            if (!emails.add(normalizedEmail)) {
                throw new IllegalArgumentException("Invalid full backup: duplicate user email " + user.getEmail());
            }
        }

        if (manifest.getUserIds() != null && !new LinkedHashSet<>(manifest.getUserIds()).equals(userIds)) {
            throw new IllegalArgumentException("Invalid full backup: manifest user ids do not match users backup");
        }

        Set<UUID> tokenIds = new HashSet<>();
        Set<String> tokenHashes = new HashSet<>();
        Set<String> oidcConnections = new HashSet<>();
        for (AdminFullBackupUsersDto.UserBackupDto user : users) {
            for (AdminFullBackupUsersDto.ApiTokenBackupDto token : nullToList(user.getApiTokens())) {
                validateApiTokenBackup(token, user.getId(), userIds, tokenIds, tokenHashes);
            }
            for (AdminFullBackupUsersDto.OidcConnectionBackupDto connection : nullToList(user.getOidcConnections())) {
                validateOidcConnectionBackup(connection, user.getId(), oidcConnections);
            }
        }
    }

    private void validateApiTokenBackup(AdminFullBackupUsersDto.ApiTokenBackupDto token,
                                        UUID userId,
                                        Set<UUID> userIds,
                                        Set<UUID> tokenIds,
                                        Set<String> tokenHashes) {
        if (token == null) {
            throw new IllegalArgumentException("Invalid full backup: empty API token for user " + userId);
        }
        if (token.getId() == null) {
            throw new IllegalArgumentException("Invalid full backup: API token id is required for user " + userId);
        }
        requireText(token.getName(), "Invalid full backup: API token name is required for user " + userId);
        requireText(token.getTokenHash(), "Invalid full backup: API token hash is required for user " + userId);
        requireText(token.getTokenPrefix(), "Invalid full backup: API token prefix is required for user " + userId);
        requireText(token.getTokenSuffix(), "Invalid full backup: API token suffix is required for user " + userId);
        if (!tokenIds.add(token.getId())) {
            throw new IllegalArgumentException("Invalid full backup: duplicate API token id " + token.getId());
        }
        if (!tokenHashes.add(token.getTokenHash().trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Invalid full backup: duplicate API token hash");
        }
        if (token.getRevokedBy() != null && !userIds.contains(token.getRevokedBy())) {
            throw new IllegalArgumentException("Invalid full backup: API token revoked_by user is not present in backup");
        }
    }

    private void validateOidcConnectionBackup(AdminFullBackupUsersDto.OidcConnectionBackupDto connection,
                                              UUID userId,
                                              Set<String> oidcConnections) {
        if (connection == null) {
            throw new IllegalArgumentException("Invalid full backup: empty OIDC connection for user " + userId);
        }
        requireText(connection.getProviderName(), "Invalid full backup: OIDC provider name is required for user " + userId);
        requireText(connection.getExternalUserId(), "Invalid full backup: OIDC external user id is required for user " + userId);
        String key = connection.getProviderName().trim().toLowerCase(Locale.ROOT)
                + "\n" + connection.getExternalUserId().trim();
        if (!oidcConnections.add(key)) {
            throw new IllegalArgumentException("Invalid full backup: duplicate OIDC provider/external user id");
        }
    }

    private void truncateRestorableState(boolean includeReferenceDatasets) {
        String referenceTables = includeReferenceDatasets
                ? ", geonames_country, geonames_city, geo_dataset_metadata, water_surface_polygons, water_dataset_state"
                : "";
        entityManager.createNativeQuery("""
                        TRUNCATE TABLE
                            users,
                            system_settings,
                            oidc_providers,
                            geocoding_provider_configs,
                            external_integration_health,
                            weather_daily_request_usage%s
                        RESTART IDENTITY CASCADE
                        """.formatted(referenceTables))
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private void exportReferenceDatasets(ZipOutputStream zos) throws IOException {
        copyOutCsv(zos, REFERENCE_GEONAMES_COUNTRY,
                "COPY (SELECT " + GEONAMES_COUNTRY_COLUMN_LIST
                        + " FROM geonames_country ORDER BY iso_alpha2) TO STDOUT WITH (FORMAT csv, HEADER true, NULL '')");
        copyOutCsv(zos, REFERENCE_GEONAMES_CITY,
                "COPY (SELECT " + GEONAMES_CITY_COLUMN_LIST
                        + " FROM geonames_city ORDER BY geonameid) TO STDOUT WITH (FORMAT csv, HEADER true, NULL '')");
        copyOutCsv(zos, REFERENCE_GEO_DATASET_METADATA,
                "COPY (SELECT " + GEO_DATASET_METADATA_COLUMN_LIST
                        + " FROM geo_dataset_metadata ORDER BY dataset_name) TO STDOUT WITH (FORMAT csv, HEADER true, NULL '')");
        copyOutCsv(zos, REFERENCE_WATER_SURFACE_POLYGONS,
                """
                        COPY (
                            SELECT id, source, source_id, name, water_type, encode(ST_AsEWKB(geom), 'hex') AS geom_ewkb_hex
                            FROM water_surface_polygons
                            ORDER BY id
                        ) TO STDOUT WITH (FORMAT csv, HEADER true, NULL '')
                        """);
        copyOutCsv(zos, REFERENCE_WATER_DATASET_STATE,
                "COPY (SELECT " + WATER_DATASET_STATE_COLUMN_LIST
                        + " FROM water_dataset_state ORDER BY dataset_key) TO STDOUT WITH (FORMAT csv, HEADER true, NULL '')");
    }

    private void restoreReferenceDatasets(BackupArchive archive) {
        copyInCsv(archive, REFERENCE_GEONAMES_COUNTRY,
                "COPY geonames_country (" + GEONAMES_COUNTRY_COLUMN_LIST
                        + ") FROM STDIN WITH (FORMAT csv, HEADER true, NULL '')");
        copyInCsv(archive, REFERENCE_GEONAMES_CITY,
                "COPY geonames_city (" + GEONAMES_CITY_COLUMN_LIST
                        + ") FROM STDIN WITH (FORMAT csv, HEADER true, NULL '')");
        copyInCsv(archive, REFERENCE_GEO_DATASET_METADATA,
                "COPY geo_dataset_metadata (" + GEO_DATASET_METADATA_COLUMN_LIST
                        + ") FROM STDIN WITH (FORMAT csv, HEADER true, NULL '')");
        restoreWaterSurfacePolygons(archive);
        copyInCsv(archive, REFERENCE_WATER_DATASET_STATE,
                "COPY water_dataset_state (" + WATER_DATASET_STATE_COLUMN_LIST
                        + ") FROM STDIN WITH (FORMAT csv, HEADER true, NULL '')");
        entityManager.createNativeQuery("ANALYZE geonames_country").executeUpdate();
        entityManager.createNativeQuery("ANALYZE geonames_city").executeUpdate();
        entityManager.createNativeQuery("ANALYZE geo_dataset_metadata").executeUpdate();
        entityManager.createNativeQuery("ANALYZE water_surface_polygons").executeUpdate();
        entityManager.createNativeQuery("ANALYZE water_dataset_state").executeUpdate();
    }

    private void restoreWaterSurfacePolygons(BackupArchive archive) {
        entityManager.createNativeQuery("DROP TABLE IF EXISTS water_surface_polygons_restore").executeUpdate();
        entityManager.createNativeQuery("""
                CREATE TEMP TABLE water_surface_polygons_restore (
                    id BIGINT,
                    source VARCHAR(100),
                    source_id TEXT,
                    name TEXT,
                    water_type VARCHAR(50),
                    geom_ewkb_hex TEXT
                ) ON COMMIT DROP
                """).executeUpdate();
        copyInCsv(archive, REFERENCE_WATER_SURFACE_POLYGONS, """
                COPY water_surface_polygons_restore (
                    id, source, source_id, name, water_type, geom_ewkb_hex
                ) FROM STDIN WITH (FORMAT csv, HEADER true, NULL '')
                """);
        entityManager.createNativeQuery("""
                INSERT INTO water_surface_polygons (id, source, source_id, name, water_type, geom)
                SELECT
                    id,
                    source,
                    source_id,
                    name,
                    water_type,
                    ST_Multi(ST_GeomFromEWKB(decode(geom_ewkb_hex, 'hex')))::geometry(MultiPolygon, 4326)
                FROM water_surface_polygons_restore
                WHERE geom_ewkb_hex IS NOT NULL AND geom_ewkb_hex <> ''
                """).executeUpdate();
    }

    private void copyOutCsv(ZipOutputStream zos, String entryName, String copySql) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        try {
            Session session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                CopyManager copyManager = connection.unwrap(PGConnection.class).getCopyAPI();
                try {
                    copyManager.copyOut(copySql, zos);
                } catch (IOException e) {
                    throw new SQLException("Failed to write backup entry " + entryName, e);
                }
            });
        } catch (RuntimeException e) {
            IOException ioException = findCause(e, IOException.class);
            if (ioException != null) {
                throw ioException;
            }
            throw e;
        } finally {
            zos.closeEntry();
        }
    }

    private void copyInCsv(BackupArchive archive, String entryName, String copySql) {
        Path path = archive.requiredPath(entryName);
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            CopyManager copyManager = connection.unwrap(PGConnection.class).getCopyAPI();
            try (InputStream input = Files.newInputStream(path)) {
                copyManager.copyIn(copySql, input);
            } catch (IOException e) {
                throw new SQLException("Failed to read backup entry " + entryName, e);
            }
        });
    }

    private void resetWaterSurfaceSequence() {
        entityManager.createNativeQuery("""
                        SELECT setval(
                            pg_get_serial_sequence('water_surface_polygons', 'id'),
                            COALESCE((SELECT MAX(id) FROM water_surface_polygons), 0) + 1,
                            false
                        )
                        """)
                .getSingleResult();
    }

    private boolean includesReferenceDatasets(AdminBackupManifestDto manifest) {
        return manifest.getSchemaVersion() >= REFERENCE_DATASET_SCHEMA_VERSION;
    }

    private UUID existingUserIdOrNull(UUID userId) {
        return userId != null && userRepository.existsById(userId) ? userId : null;
    }

    private void runInNewTransaction(IoRunnable runnable) throws IOException {
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                try {
                    runnable.run();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        } catch (RuntimeException e) {
            UncheckedIOException ioException = findCause(e, UncheckedIOException.class);
            if (ioException != null) {
                throw ioException.getCause();
            }
            throw e;
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
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

    private void restoreUserAuthData(UUID userId, AdminFullBackupUsersDto.UserBackupDto dto) {
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
                    .setParameter("userId", userId)
                    .setParameter("name", tokenDto.getName())
                    .setParameter("tokenHash", tokenDto.getTokenHash())
                    .setParameter("tokenPrefix", tokenDto.getTokenPrefix())
                    .setParameter("tokenSuffix", tokenDto.getTokenSuffix())
                    .setParameter("createdAt", defaultInstant(tokenDto.getCreatedAt()))
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
                    .setParameter("userId", userId)
                    .setParameter("providerName", connectionDto.getProviderName())
                    .setParameter("externalUserId", connectionDto.getExternalUserId())
                    .setParameter("displayName", connectionDto.getDisplayName())
                    .setParameter("avatarUrl", connectionDto.getAvatarUrl())
                    .setParameter("linkedAt", defaultInstant(connectionDto.getLinkedAt()))
                    .setParameter("lastLoginAt", connectionDto.getLastLoginAt())
                    .executeUpdate();
        }
        entityManager.flush();
        entityManager.clear();
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

    private BackupArchive extractZip(byte[] bytes, Instant deadline) throws IOException {
        Path root = Files.createTempDirectory("geopulse-full-restore-");
        Map<String, Path> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ensureNotTimedOut(deadline);
                if (!entry.isDirectory()) {
                    Path relativePath = safeZipEntryPath(entry.getName());
                    Path target = root.resolve(relativePath).normalize();
                    if (!target.startsWith(root)) {
                        throw new IllegalArgumentException("Invalid full backup: unsafe zip entry " + entry.getName());
                    }
                    if (entries.containsKey(entry.getName())) {
                        throw new IllegalArgumentException("Invalid full backup: duplicate zip entry " + entry.getName());
                    }
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                    entries.put(entry.getName(), target);
                }
                zis.closeEntry();
            }
            return new BackupArchive(root, entries);
        } catch (IOException | RuntimeException e) {
            deleteRecursively(root);
            throw e;
        }
    }

    private Path safeZipEntryPath(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            throw new IllegalArgumentException("Invalid full backup: empty zip entry name");
        }
        Path path = Paths.get(entryName).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            throw new IllegalArgumentException("Invalid full backup: unsafe zip entry " + entryName);
        }
        return path;
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
        return 25 + Math.min(70, (int) Math.round((processedUsers * 70.0) / totalUsers));
    }

    private int restoreProgressPercent(int processedUsers, int totalUsers) {
        if (totalUsers <= 0) {
            return 95;
        }
        return 40 + Math.min(55, (int) Math.round((processedUsers * 55.0) / totalUsers));
    }

    private Instant defaultInstant(Instant value) {
        return value == null ? Instant.now() : value;
    }

    private <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    @FunctionalInterface
    private interface IoRunnable {
        void run() throws IOException;
    }

    private static final class BackupArchive implements AutoCloseable {
        private final Path root;
        private final Map<String, Path> entries;

        private BackupArchive(Path root, Map<String, Path> entries) {
            this.root = root;
            this.entries = entries;
        }

        private boolean hasEntry(String entryName) {
            return entries.containsKey(entryName);
        }

        private Optional<Path> entryPath(String entryName) {
            return Optional.ofNullable(entries.get(entryName));
        }

        private Path requiredPath(String entryName) {
            Path path = entries.get(entryName);
            if (path == null) {
                throw new IllegalArgumentException("Invalid full backup: missing " + entryName);
            }
            return path;
        }

        private byte[] readRequired(String entryName) throws IOException {
            return Files.readAllBytes(requiredPath(entryName));
        }

        @Override
        public void close() throws IOException {
            deleteRecursively(root);
        }
    }
}
