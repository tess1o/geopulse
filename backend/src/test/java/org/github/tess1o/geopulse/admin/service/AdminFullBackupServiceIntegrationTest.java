package org.github.tess1o.geopulse.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.auth.model.UserApiTokenEntity;
import org.github.tess1o.geopulse.auth.oidc.model.UserOidcConnectionEntity;
import org.github.tess1o.geopulse.export.dto.LocationSourcesDataDto;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.friends.model.UserFriendEntity;
import org.github.tess1o.geopulse.friends.model.UserFriendPermissionEntity;
import org.github.tess1o.geopulse.geocoding.model.ReverseGeocodingLocationEntity;
import org.github.tess1o.geopulse.gps.model.GpsPointEntity;
import org.github.tess1o.geopulse.gpssource.model.GpsSourceConfigEntity;
import org.github.tess1o.geopulse.mapmatching.model.MapMatchingStatus;
import org.github.tess1o.geopulse.mapmatching.model.TimelineTripPathMatchEntity;
import org.github.tess1o.geopulse.notes.model.NoteAnchorType;
import org.github.tess1o.geopulse.notes.model.NoteLocationSource;
import org.github.tess1o.geopulse.notes.model.TimelineNoteEntity;
import org.github.tess1o.geopulse.shared.geo.GeoUtils;
import org.github.tess1o.geopulse.shared.gps.GpsSourceType;
import org.github.tess1o.geopulse.streaming.model.domain.LocationSource;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineDataGapEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineStayEntity;
import org.github.tess1o.geopulse.streaming.model.entity.TimelineTripEntity;
import org.github.tess1o.geopulse.streaming.model.shared.MovementTypeSource;
import org.github.tess1o.geopulse.streaming.service.TimelineJobProgressService;
import org.github.tess1o.geopulse.shared.exportimport.ExportImportConstants;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class AdminFullBackupServiceIntegrationTest {

    @Inject
    AdminFullBackupService backupService;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    TimelineJobProgressService timelineJobProgressService;

    @Inject
    AIEncryptionService encryptionService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private UUID ownerId;
    private UUID friendId;
    private UUID tokenId;
    private UUID gpsSourceId;
    private Long geocodingId;
    private Long stayId;
    private Long tripId;
    private Long dataGapId;

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() ->
                entityManager.createNativeQuery("TRUNCATE TABLE users CASCADE").executeUpdate());
        QuarkusTransaction.requiringNew().run(this::seedBackupData);
    }

    @Test
    void restoreFullBackupCanReplaceExistingAuthAndFriendRows() throws Exception {
        byte[] backup = exportFullBackup();
        assertPortableSecretsInArchive(backup);
        long timelineJobsBeforeRestore = (long) timelineJobProgressService.getStatistics().get("totalJobs");

        backupService.restoreFullBackup(backup, ownerId);

        QuarkusTransaction.requiringNew().run(() -> {
            UserApiTokenEntity token = entityManager.find(UserApiTokenEntity.class, tokenId);
            assertThat(token).isNotNull();
            assertThat(token.getUser().getId()).isEqualTo(ownerId);
            assertThat(token.getName()).isEqualTo("mobile");
            assertThat(token.getTokenHash()).isEqualTo("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

            UserEntity owner = entityManager.find(UserEntity.class, ownerId);
            assertThat(owner.getPasswordHash()).isEqualTo("hash");
            UserAISettings aiSettings = decryptStoredAiSettings(owner);
            assertThat(aiSettings.isEnabled()).isTrue();
            assertThat(aiSettings.getOpenaiApiUrl()).isEqualTo("https://ai.example.test/v1");
            assertThat(aiSettings.getOpenaiModel()).isEqualTo("gpt-test");
            assertThat(aiSettings.getOpenaiApiKey()).isEqualTo("sk-portable-full-backup");
            assertThat(aiSettings.isOpenaiApiKeyConfigured()).isTrue();
            assertThat(aiSettings.getCustomSystemMessage()).isEqualTo("portable system prompt");

            UserEntity friend = entityManager.find(UserEntity.class, friendId);
            UserAISettings friendAiSettings = decryptStoredAiSettings(friend);
            assertThat(friendAiSettings.getOpenaiApiKey()).isEqualTo("sk-plaintext-dev-backup");
            assertThat(friendAiSettings.isOpenaiApiKeyConfigured()).isTrue();

            GpsSourceConfigEntity gpsSource = entityManager.find(GpsSourceConfigEntity.class, gpsSourceId);
            assertThat(gpsSource).isNotNull();
            assertThat(gpsSource.getUser().getId()).isEqualTo(ownerId);
            assertThat(gpsSource.getSourceType()).isEqualTo(GpsSourceType.OWNTRACKS);
            assertThat(gpsSource.getUsername()).isEqualTo("owntracks-owner");
            assertThat(gpsSource.getPasswordHash()).isEqualTo("owntracks-password-hash");
            assertThat(gpsSource.getToken()).isEqualTo("source-token");
            assertThat(gpsSource.getDeviceId()).isEqualTo("phone-1");
            assertThat(gpsSource.getConnectionType()).isEqualTo(GpsSourceConfigEntity.ConnectionType.MQTT);
            assertThat(gpsSource.isActive()).isTrue();
            assertThat(gpsSource.isFilterInaccurateData()).isTrue();
            assertThat(gpsSource.getMaxAllowedAccuracy()).isEqualTo(42);
            assertThat(gpsSource.getMaxAllowedSpeed()).isEqualTo(130);
            assertThat(gpsSource.isEnableDuplicateDetection()).isTrue();
            assertThat(gpsSource.getDuplicateDetectionThresholdMinutes()).isEqualTo(15);
            assertThat(encryptionService.decrypt(
                    gpsSource.getPayloadEncryptionSecretEncrypted(),
                    gpsSource.getPayloadEncryptionSecretKeyId()
            )).isEqualTo("owntracks-payload-secret");

            Long oidcConnections = entityManager.createQuery("""
                            SELECT COUNT(connection)
                            FROM UserOidcConnectionEntity connection
                            WHERE connection.userId = :userId AND connection.providerName = :provider
                            """, Long.class)
                    .setParameter("userId", ownerId)
                    .setParameter("provider", "test-oidc")
                    .getSingleResult();
            assertThat(oidcConnections).isEqualTo(1);

            Long friendships = entityManager.createQuery("""
                            SELECT COUNT(friendship)
                            FROM UserFriendEntity friendship
                            WHERE friendship.user.id = :ownerId AND friendship.friend.id = :friendId
                            """, Long.class)
                    .setParameter("ownerId", ownerId)
                    .setParameter("friendId", friendId)
                    .getSingleResult();
            assertThat(friendships).isEqualTo(1);

            UserFriendPermissionEntity permission = entityManager.createQuery("""
                            SELECT permission
                            FROM UserFriendPermissionEntity permission
                            WHERE permission.user.id = :friendId AND permission.friend.id = :ownerId
                            """, UserFriendPermissionEntity.class)
                    .setParameter("friendId", friendId)
                    .setParameter("ownerId", ownerId)
                    .getSingleResult();
            assertThat(permission.getShareTimeline()).isTrue();
            assertThat(permission.getShareLiveLocation()).isTrue();

            TimelineStayEntity stay = entityManager.find(TimelineStayEntity.class, stayId);
            assertThat(stay).isNotNull();
            assertThat(stay.getUser().getId()).isEqualTo(ownerId);
            assertThat(stay.getLocationName()).isEqualTo("Snapshot Cafe");
            assertThat(stay.getGeocodingLocation().getId()).isEqualTo(geocodingId);

            TimelineTripEntity trip = entityManager.find(TimelineTripEntity.class, tripId);
            assertThat(trip).isNotNull();
            assertThat(trip.getUser().getId()).isEqualTo(ownerId);
            assertThat(trip.getMovementType()).isEqualTo("WALKING");

            TimelineDataGapEntity dataGap = entityManager.find(TimelineDataGapEntity.class, dataGapId);
            assertThat(dataGap).isNotNull();
            assertThat(dataGap.getUser().getId()).isEqualTo(ownerId);

            TimelineNoteEntity note = entityManager.createQuery("""
                            SELECT note FROM TimelineNoteEntity note
                            WHERE note.user.id = :userId AND note.title = :title
                            """, TimelineNoteEntity.class)
                    .setParameter("userId", ownerId)
                    .setParameter("title", "Snapshot note")
                    .getSingleResult();
            assertThat(note.getStay().getId()).isEqualTo(stayId);

            TimelineTripPathMatchEntity pathMatch = entityManager.createQuery("""
                            SELECT pathMatch FROM TimelineTripPathMatchEntity pathMatch
                            WHERE pathMatch.user.id = :userId AND pathMatch.trip.id = :tripId
                            """, TimelineTripPathMatchEntity.class)
                    .setParameter("userId", ownerId)
                    .setParameter("tripId", tripId)
                    .getSingleResult();
            assertThat(pathMatch.getStatus()).isEqualTo(MapMatchingStatus.MATCHED);
        });
        assertThat((long) timelineJobProgressService.getStatistics().get("totalJobs"))
                .isEqualTo(timelineJobsBeforeRestore);
    }

    private byte[] exportFullBackup() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(output)) {
            backupService.writeFullBackup(zos);
        }
        return output.toByteArray();
    }

    private void assertPortableSecretsInArchive(byte[] backup) throws Exception {
        Set<String> entries = zipEntryNames(backup);
        assertThat(entries).noneMatch(name -> name.contains("jwt-private-key.pem"));
        assertThat(entries).noneMatch(name -> name.contains("jwt-public-key.pem"));

        String usersJson = new String(readZipEntry(backup, "users/users.json"), StandardCharsets.UTF_8);
        assertThat(usersJson).contains("\"aiSettings\"");
        assertThat(usersJson).contains("sk-portable-full-backup");
        assertThat(usersJson).contains("sk-plaintext-dev-backup");
        assertThat(usersJson).doesNotContain("aiSettingsEncrypted");
        assertThat(usersJson).doesNotContain("aiSettingsKeyId");

        byte[] nestedExport = readZipEntry(backup, "users/" + ownerId + "/geopulse-export.zip");
        String locationSourcesJson = new String(
                readZipEntry(nestedExport, ExportImportConstants.FileNames.LOCATION_SOURCES),
                StandardCharsets.UTF_8
        );
        LocationSourcesDataDto sources = objectMapper.readValue(locationSourcesJson, LocationSourcesDataDto.class);
        assertThat(sources.getSources()).singleElement().satisfies(source -> {
            assertThat(source.getId()).isEqualTo(gpsSourceId);
            assertThat(source.getType()).isEqualTo("OWNTRACKS");
            assertThat(source.getUsername()).isEqualTo("owntracks-owner");
            assertThat(source.getPasswordHash()).isEqualTo("owntracks-password-hash");
            assertThat(source.getToken()).isEqualTo("source-token");
            assertThat(source.getDeviceId()).isEqualTo("phone-1");
            assertThat(source.getPayloadEncryptionSecret()).isEqualTo("owntracks-payload-secret");
            assertThat(source.getConnectionType()).isEqualTo("MQTT");
            assertThat(source.isFilterInaccurateData()).isTrue();
            assertThat(source.getMaxAllowedAccuracy()).isEqualTo(42);
            assertThat(source.getMaxAllowedSpeed()).isEqualTo(130);
            assertThat(source.isEnableDuplicateDetection()).isTrue();
            assertThat(source.getDuplicateDetectionThresholdMinutes()).isEqualTo(15);
        });
    }

    private UserAISettings decryptStoredAiSettings(UserEntity user) {
        try {
            String json = encryptionService.decrypt(user.getAiSettingsEncrypted(), user.getAiSettingsKeyId());
            UserAISettings settings = objectMapper.readValue(json, UserAISettings.class);
            settings.setOpenaiApiKey(encryptionService.decrypt(settings.getOpenaiApiKey(), user.getAiSettingsKeyId()));
            return settings;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt stored AI settings", e);
        }
    }

    private Set<String> zipEntryNames(byte[] zipBytes) throws Exception {
        Set<String> names = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
                zis.closeEntry();
            }
        }
        return names;
    }

    private byte[] readZipEntry(byte[] zipBytes, String entryName) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return zis.readAllBytes();
                }
                zis.closeEntry();
            }
        }
        throw new AssertionError("Missing zip entry: " + entryName);
    }

    private void seedBackupData() {
        UserEntity owner = createUser("owner-full-backup@example.com", Role.ADMIN);
        UserEntity friend = createUser("friend-full-backup@example.com", Role.USER);
        ownerId = owner.getId();
        friendId = friend.getId();
        seedAiSettings(owner);
        seedPlaintextNestedAiSettings(friend);

        GpsSourceConfigEntity gpsSource = GpsSourceConfigEntity.builder()
                .user(owner)
                .sourceType(GpsSourceType.OWNTRACKS)
                .username("owntracks-owner")
                .passwordHash("owntracks-password-hash")
                .token("source-token")
                .deviceId("phone-1")
                .payloadEncryptionSecretEncrypted(encryptionService.encrypt("owntracks-payload-secret"))
                .payloadEncryptionSecretKeyId(encryptionService.getCurrentKeyId())
                .active(true)
                .connectionType(GpsSourceConfigEntity.ConnectionType.MQTT)
                .filterInaccurateData(true)
                .maxAllowedAccuracy(42)
                .maxAllowedSpeed(130)
                .enableDuplicateDetection(true)
                .duplicateDetectionThresholdMinutes(15)
                .build();
        entityManager.persist(gpsSource);

        UserApiTokenEntity token = UserApiTokenEntity.builder()
                .user(owner)
                .name("mobile")
                .tokenHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .tokenPrefix("gp_test")
                .tokenSuffix("abcdef")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        entityManager.persist(token);

        UserOidcConnectionEntity oidcConnection = UserOidcConnectionEntity.builder()
                .userId(owner.getId())
                .user(owner)
                .providerName("test-oidc")
                .externalUserId("external-owner")
                .displayName("Owner")
                .avatarUrl("https://example.com/avatar.png")
                .linkedAt(Instant.parse("2026-01-02T00:00:00Z"))
                .lastLoginAt(Instant.parse("2026-01-03T00:00:00Z"))
                .build();
        entityManager.persist(oidcConnection);

        UserFriendEntity friendship = new UserFriendEntity();
        friendship.setUser(owner);
        friendship.setFriend(friend);
        entityManager.persist(friendship);

        UserFriendPermissionEntity permission = new UserFriendPermissionEntity();
        permission.setUser(friend);
        permission.setFriend(owner);
        permission.setShareTimeline(true);
        permission.setShareLiveLocation(true);
        entityManager.persist(permission);

        ReverseGeocodingLocationEntity geocoding = new ReverseGeocodingLocationEntity();
        geocoding.setUser(owner);
        geocoding.setRequestCoordinates(GeoUtils.createPoint(30.5234, 50.4501));
        geocoding.setResultCoordinates(GeoUtils.createPoint(30.5234, 50.4501));
        geocoding.setDisplayName("Snapshot Cafe");
        geocoding.setProviderName("test");
        geocoding.setCreatedAt(Instant.parse("2026-01-04T00:00:00Z"));
        geocoding.setLastAccessedAt(Instant.parse("2026-01-04T00:00:00Z"));
        geocoding.setCity("Kyiv");
        geocoding.setCountry("Ukraine");
        entityManager.persist(geocoding);

        GpsPointEntity gpsPoint = new GpsPointEntity();
        gpsPoint.setUser(owner);
        gpsPoint.setDeviceId("test-device");
        gpsPoint.setCoordinates(GeoUtils.createPoint(30.5234, 50.4501));
        gpsPoint.setTimestamp(Instant.parse("2026-01-04T10:00:00Z"));
        gpsPoint.setAccuracy(5.0);
        gpsPoint.setSourceType(GpsSourceType.OWNTRACKS);
        gpsPoint.setCreatedAt(Instant.parse("2026-01-04T10:00:01Z"));
        entityManager.persist(gpsPoint);

        TimelineStayEntity stay = TimelineStayEntity.builder()
                .user(owner)
                .timestamp(Instant.parse("2026-01-04T10:00:00Z"))
                .stayDuration(3600)
                .location(GeoUtils.createPoint(30.5234, 50.4501))
                .locationName("Snapshot Cafe")
                .geocodingLocation(geocoding)
                .createdAt(Instant.parse("2026-01-04T10:00:00Z"))
                .lastUpdated(Instant.parse("2026-01-04T10:00:00Z"))
                .locationSource(LocationSource.GEOCODING)
                .build();
        entityManager.persist(stay);

        TimelineTripEntity trip = TimelineTripEntity.builder()
                .user(owner)
                .timestamp(Instant.parse("2026-01-04T11:00:00Z"))
                .tripDuration(900)
                .startPoint(GeoUtils.createPoint(30.5234, 50.4501))
                .endPoint(GeoUtils.createPoint(30.5300, 50.4550))
                .distanceMeters(1200)
                .movementType("WALKING")
                .movementTypeSource(MovementTypeSource.AUTO)
                .createdAt(Instant.parse("2026-01-04T11:00:00Z"))
                .lastUpdated(Instant.parse("2026-01-04T11:00:00Z"))
                .build();
        entityManager.persist(trip);

        TimelineDataGapEntity dataGap = TimelineDataGapEntity.builder()
                .user(owner)
                .startTime(Instant.parse("2026-01-04T12:00:00Z"))
                .endTime(Instant.parse("2026-01-04T13:00:00Z"))
                .durationSeconds(3600)
                .createdAt(Instant.parse("2026-01-04T12:00:00Z"))
                .build();
        entityManager.persist(dataGap);

        TimelineNoteEntity note = TimelineNoteEntity.builder()
                .user(owner)
                .title("Snapshot note")
                .contentMarkdown("A note anchored to the restored stay")
                .snippet("A note")
                .eventTime(Instant.parse("2026-01-04T10:30:00Z"))
                .location(GeoUtils.createPoint(30.5234, 50.4501))
                .locationSource(NoteLocationSource.DERIVED_STAY)
                .anchorType(NoteAnchorType.STAY)
                .stay(stay)
                .sourceItemStartTime(stay.getTimestamp())
                .sourceItemDurationSeconds(stay.getStayDuration())
                .createdAt(Instant.parse("2026-01-04T10:30:00Z"))
                .updatedAt(Instant.parse("2026-01-04T10:30:00Z"))
                .build();
        entityManager.persist(note);

        TimelineTripPathMatchEntity pathMatch = TimelineTripPathMatchEntity.builder()
                .trip(trip)
                .user(owner)
                .provider("valhalla")
                .profile("pedestrian")
                .configHash("config-hash")
                .inputHash("input-hash")
                .status(MapMatchingStatus.MATCHED)
                .attempts(1)
                .nextAttemptAt(Instant.parse("2026-01-04T11:00:00Z"))
                .completedAt(Instant.parse("2026-01-04T11:01:00Z"))
                .matchedSegmentsJson("{\"shape\":[]}")
                .source("ON_DEMAND")
                .priority(100)
                .createdAt(Instant.parse("2026-01-04T11:00:00Z"))
                .updatedAt(Instant.parse("2026-01-04T11:01:00Z"))
                .build();
        entityManager.persist(pathMatch);

        entityManager.flush();
        tokenId = token.getId();
        gpsSourceId = gpsSource.getId();
        geocodingId = geocoding.getId();
        stayId = stay.getId();
        tripId = trip.getId();
        dataGapId = dataGap.getId();
    }

    private void seedAiSettings(UserEntity owner) {
        try {
            UserAISettings settings = UserAISettings.builder()
                    .enabled(true)
                    .openaiApiKey(encryptionService.encrypt("sk-portable-full-backup"))
                    .openaiApiUrl("https://ai.example.test/v1")
                    .openaiModel("gpt-test")
                    .openaiApiKeyConfigured(true)
                    .apiKeyRequired(true)
                    .customSystemMessage("portable system prompt")
                    .build();
            owner.setAiSettingsEncrypted(encryptionService.encrypt(objectMapper.writeValueAsString(settings)));
            owner.setAiSettingsKeyId(encryptionService.getCurrentKeyId());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to seed AI settings", e);
        }
    }

    private void seedPlaintextNestedAiSettings(UserEntity user) {
        try {
            UserAISettings settings = UserAISettings.builder()
                    .enabled(true)
                    .openaiApiKey("sk-plaintext-dev-backup")
                    .openaiApiUrl("https://ai.example.test/v1")
                    .openaiModel("gpt-test")
                    .openaiApiKeyConfigured(true)
                    .apiKeyRequired(true)
                    .customSystemMessage("plaintext nested key")
                    .build();
            user.setAiSettingsEncrypted(encryptionService.encrypt(objectMapper.writeValueAsString(settings)));
            user.setAiSettingsKeyId(encryptionService.getCurrentKeyId());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to seed plaintext nested AI settings", e);
        }
    }

    private UserEntity createUser(String email, Role role) {
        UserEntity user = UserEntity.builder()
                .email(email)
                .emailVerified(true)
                .passwordHash("hash")
                .fullName(email)
                .isActive(true)
                .role(role)
                .timezone("UTC")
                .build();
        userRepository.persist(user);
        return user;
    }
}
