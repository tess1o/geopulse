package org.github.tess1o.geopulse.importdata.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.export.dto.*;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.gpssource.repository.GpsSourceRepository;
import org.github.tess1o.geopulse.importdata.mapper.ImportDataMapper;
import org.github.tess1o.geopulse.importdata.model.ImportJob;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.user.model.TimelinePreferences;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.IMPORT_JSON;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.defaultInstant;
import static org.github.tess1o.geopulse.importdata.service.GeoPulseImportValues.emptyIfNull;

/**
 * GeoPulse import of account data: user preferences, location sources, friends and friend permissions.
 */
@ApplicationScoped
@Slf4j
public class GeoPulseAccountImporter {

    @Inject
    UserRepository userRepository;

    @Inject
    ImportDataMapper importDataMapper;

    @Inject
    GpsSourceRepository gpsSourceRepository;

    @Inject
    AIEncryptionService encryptionService;

    @Inject
    EntityManager entityManager;

    @Transactional
    public void importUserInfoData(byte[] content, ImportJob job, String exportVersion) throws IOException {
        UserInfoDataDto userInfoData = IMPORT_JSON.readValue(content, UserInfoDataDto.class);
        log.info("Importing user info for user {}", job.getUserId());

        UserEntity user = userRepository.findById(job.getUserId());
        if (user == null) {
            throw new IllegalStateException("User not found: " + job.getUserId());
        }

        UserInfoDataDto.UserDto userData = userInfoData.getUser();

        // Update user preferences if they exist in the import
        if (userData.getPreferences() != null) {
            convertLegacyVelocityThreshold(userData.getPreferences(), exportVersion);
            user.setTimelinePreferences(importDataMapper.updateTimelinePreferences(
                    userData.getPreferences(), user.getTimelinePreferences()));
        }

        userRepository.persist(user);
    }

    /**
     * Exports before version 1.2 hold the stay velocity threshold in m/s; it is in km/h now.
     */
    private void convertLegacyVelocityThreshold(TimelinePreferences preferences, String exportVersion) {
        boolean legacyVersion = ExportImportConstants.Versions.V1_0.equals(exportVersion)
                || ExportImportConstants.Versions.V1_1.equals(exportVersion);
        if (legacyVersion && preferences.getStaypointVelocityThreshold() != null) {
            preferences.setStaypointVelocityThreshold(preferences.getStaypointVelocityThreshold() * 3.6);
        }
    }

    @Transactional
    public void importLocationSourcesData(byte[] content, ImportJob job) throws IOException {
        LocationSourcesDataDto sourcesData = IMPORT_JSON.readValue(content, LocationSourcesDataDto.class);
        log.info("Importing {} location sources for user {} using duplicate detection", sourcesData.getSources().size(), job.getUserId());

        UserEntity user = userRepository.findById(job.getUserId());
        if (user == null) {
            throw new IllegalStateException("User not found: " + job.getUserId());
        }

        int imported = 0;
        int skipped = 0;

        for (LocationSourcesDataDto.SourceDto sourceDto : sourcesData.getSources()) {
            try {
                // Convert string type to enum
                GpsSourceType sourceType = GpsSourceType.valueOf(sourceDto.getType());

                Optional<GpsSourceConfigEntity> existingById = sourceDto.getId() == null
                        ? Optional.empty()
                        : gpsSourceRepository.findByConfigIdAndUserId(sourceDto.getId(), job.getUserId());
                List<GpsSourceConfigEntity> duplicates = existingById
                        .map(List::of)
                        .orElseGet(() -> gpsSourceRepository.findByUserAndUsernameAndType(
                                job.getUserId(), sourceDto.getUsername(), sourceType));

                if (duplicates.isEmpty()) {
                    GpsSourceConfigEntity sourceConfig = new GpsSourceConfigEntity();
                    if (sourceDto.getId() != null) {
                        sourceConfig.setId(sourceDto.getId());
                    }
                    applyLocationSourceDto(sourceConfig, user, sourceDto, sourceType);
                    persistNewLocationSource(sourceConfig);
                    imported++;
                } else {
                    GpsSourceConfigEntity existing = duplicates.get(0);
                    applyLocationSourceDto(existing, user, sourceDto, sourceType);
                    skipped++;
                }
            } catch (Exception e) {
                log.warn("Failed to import GPS source '{}': {}", sourceDto.getUsername(), e.getMessage());
            }
        }

        log.info("Successfully imported {} GPS sources using duplicate detection (skipped {} duplicates)", imported, skipped);
    }

    private void persistNewLocationSource(GpsSourceConfigEntity sourceConfig) {
        if (sourceConfig.getId() == null) {
            gpsSourceRepository.persist(sourceConfig);
        } else {
            entityManager.createNativeQuery("""
                            INSERT INTO gps_source_config (
                                id,
                                user_id,
                                username,
                                password_hash,
                                token,
                                device_id,
                                payload_encryption_secret_encrypted,
                                payload_encryption_secret_key_id,
                                source_type,
                                active,
                                connection_type,
                                filter_inaccurate_data,
                                max_allowed_accuracy,
                                max_allowed_speed,
                                enable_duplicate_detection,
                                duplicate_detection_threshold_minutes
                            ) VALUES (
                                :id,
                                :userId,
                                :username,
                                :passwordHash,
                                :token,
                                :deviceId,
                                :payloadEncryptionSecretEncrypted,
                                :payloadEncryptionSecretKeyId,
                                :sourceType,
                                :active,
                                :connectionType,
                                :filterInaccurateData,
                                :maxAllowedAccuracy,
                                :maxAllowedSpeed,
                                :enableDuplicateDetection,
                                :duplicateDetectionThresholdMinutes
                            )
                            """)
                    .setParameter("id", sourceConfig.getId())
                    .setParameter("userId", sourceConfig.getUser().getId())
                    .setParameter("username", sourceConfig.getUsername())
                    .setParameter("passwordHash", sourceConfig.getPasswordHash())
                    .setParameter("token", sourceConfig.getToken())
                    .setParameter("deviceId", sourceConfig.getDeviceId())
                    .setParameter("payloadEncryptionSecretEncrypted", sourceConfig.getPayloadEncryptionSecretEncrypted())
                    .setParameter("payloadEncryptionSecretKeyId", sourceConfig.getPayloadEncryptionSecretKeyId())
                    .setParameter("sourceType", sourceConfig.getSourceType().name())
                    .setParameter("active", sourceConfig.isActive())
                    .setParameter("connectionType", sourceConfig.getConnectionType().name())
                    .setParameter("filterInaccurateData", sourceConfig.isFilterInaccurateData())
                    .setParameter("maxAllowedAccuracy", sourceConfig.getMaxAllowedAccuracy())
                    .setParameter("maxAllowedSpeed", sourceConfig.getMaxAllowedSpeed())
                    .setParameter("enableDuplicateDetection", sourceConfig.isEnableDuplicateDetection())
                    .setParameter("duplicateDetectionThresholdMinutes", sourceConfig.getDuplicateDetectionThresholdMinutes())
                    .executeUpdate();
        }
    }

    private void applyLocationSourceDto(GpsSourceConfigEntity target,
                                        UserEntity user,
                                        LocationSourcesDataDto.SourceDto sourceDto,
                                        GpsSourceType sourceType) {
        target.setUser(user);
        target.setSourceType(sourceType);
        target.setUsername(sourceDto.getUsername());
        target.setPasswordHash(sourceDto.getPasswordHash());
        target.setToken(sourceDto.getToken());
        target.setDeviceId(sourceDto.getDeviceId());
        target.setActive(sourceDto.isActive());
        target.setConnectionType(parseConnectionType(sourceDto.getConnectionType()));
        target.setFilterInaccurateData(sourceDto.isFilterInaccurateData());
        target.setMaxAllowedAccuracy(sourceDto.getMaxAllowedAccuracy());
        target.setMaxAllowedSpeed(sourceDto.getMaxAllowedSpeed());
        target.setEnableDuplicateDetection(sourceDto.isEnableDuplicateDetection());
        target.setDuplicateDetectionThresholdMinutes(sourceDto.getDuplicateDetectionThresholdMinutes());
        if (sourceDto.getPayloadEncryptionSecret() == null || sourceDto.getPayloadEncryptionSecret().isBlank()) {
            target.setPayloadEncryptionSecretEncrypted(null);
            target.setPayloadEncryptionSecretKeyId(null);
        } else {
            target.setPayloadEncryptionSecretEncrypted(encryptionService.encrypt(sourceDto.getPayloadEncryptionSecret()));
            target.setPayloadEncryptionSecretKeyId(encryptionService.getCurrentKeyId());
        }
    }

    private GpsSourceConfigEntity.ConnectionType parseConnectionType(String connectionType) {
        if (connectionType == null || connectionType.isBlank()) {
            return GpsSourceConfigEntity.ConnectionType.HTTP;
        }
        return GpsSourceConfigEntity.ConnectionType.valueOf(connectionType);
    }

    @Transactional
    public void importFriendsData(byte[] content, ImportJob job) throws IOException {
        FriendsDataDto data = IMPORT_JSON.readValue(content, FriendsDataDto.class);
        int imported = 0;
        for (FriendsDataDto.FriendDto dto : emptyIfNull(data.getFriends())) {
            UserEntity user = resolveUser(dto.getUserId(), dto.getUserEmail()).orElse(null);
            UserEntity friend = resolveUser(dto.getFriendId(), dto.getFriendEmail()).orElse(null);
            if (user == null || friend == null || user.getId().equals(friend.getId())) {
                continue;
            }
            imported += entityManager.createNativeQuery("""
                            INSERT INTO user_friends (user_id, friend_id, created_at)
                            VALUES (:userId, :friendId, :createdAt)
                            ON CONFLICT (user_id, friend_id) DO NOTHING
                            """)
                    .setParameter("userId", user.getId())
                    .setParameter("friendId", friend.getId())
                    .setParameter("createdAt", defaultInstant(dto.getCreatedAt()))
                    .executeUpdate();
        }
        log.info("Imported {} directed friend relationships", imported);
    }

    @Transactional
    public void importFriendPermissionsData(byte[] content, ImportJob job) throws IOException {
        FriendPermissionsDataDto data = IMPORT_JSON.readValue(content, FriendPermissionsDataDto.class);
        int importedOrUpdated = 0;
        for (FriendPermissionsDataDto.FriendPermissionDto dto : emptyIfNull(data.getPermissions())) {
            UserEntity user = resolveUser(dto.getUserId(), dto.getUserEmail()).orElse(null);
            UserEntity friend = resolveUser(dto.getFriendId(), dto.getFriendEmail()).orElse(null);
            if (user == null || friend == null || user.getId().equals(friend.getId())) {
                continue;
            }

            importedOrUpdated += entityManager.createNativeQuery("""
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
                    .setParameter("userId", user.getId())
                    .setParameter("friendId", friend.getId())
                    .setParameter("shareTimeline", Boolean.TRUE.equals(dto.getShareTimeline()))
                    .setParameter("shareLiveLocation", Boolean.TRUE.equals(dto.getShareLiveLocation()))
                    .setParameter("createdAt", defaultInstant(dto.getCreatedAt()))
                    .setParameter("updatedAt", defaultInstant(dto.getUpdatedAt()))
                    .executeUpdate();
        }
        log.info("Imported/updated {} friend permission records", importedOrUpdated);
    }

    private Optional<UserEntity> resolveUser(UUID userId, String email) {
        if (userId != null) {
            UserEntity user = userRepository.findById(userId);
            if (user != null) {
                return Optional.of(user);
            }
        }
        return userRepository.findByEmailIgnoreCase(email);
    }
}
