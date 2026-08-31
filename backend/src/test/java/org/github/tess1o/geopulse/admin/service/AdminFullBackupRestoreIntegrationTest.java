package org.github.tess1o.geopulse.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.github.tess1o.geopulse.admin.dto.AdminSettingsBackupDto;
import org.github.tess1o.geopulse.admin.dto.AdminSettingsImportResult;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupManifestDto;
import org.github.tess1o.geopulse.admin.dto.backup.AdminBackupStatusDto;
import org.github.tess1o.geopulse.admin.dto.backup.AdminFullBackupUsersDto;
import org.github.tess1o.geopulse.admin.model.OidcProviderEntity;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.shared.map.MapRenderMode;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.model.DistanceUnit;
import org.github.tess1o.geopulse.user.model.TemperatureUnit;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
@QuarkusTestResource(
        value = PostgisTestResource.class,
        initArgs = @ResourceArg(name = PostgisTestResource.DATABASE_NAME_ARG, value = "gp_full_backup_restore_test"),
        restrictToAnnotatedClass = true
)
@TestProfile(AdminFullBackupRestoreIntegrationTest.EnvironmentOidcProviderProfile.class)
@SerializedDatabaseTest
class AdminFullBackupRestoreIntegrationTest {
    private static final UUID LOCAL_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BACKUP_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_BACKUP_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID BACKUP_API_TOKEN_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final String BACKUP_API_TOKEN_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Inject
    AdminSettingsBackupService settingsBackupService;

    @Inject
    AdminFullBackupService fullBackupService;

    @Inject
    BackupMaintenanceService maintenanceService;

    @Inject
    OidcProviderConfigurationService oidcProviderConfigurationService;

    @Inject
    EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("DELETE FROM admin_restore_operations").executeUpdate();
            entityManager.createNativeQuery("""
                            TRUNCATE TABLE
                                users,
                                system_settings,
                                geocoding_provider_configs,
                                oidc_providers,
                                external_integration_health,
                                weather_daily_request_usage,
                                geonames_country,
                                geonames_city,
                                geo_dataset_metadata,
                                water_surface_polygons,
                                water_dataset_state
                            RESTART IDENTITY CASCADE
                            """)
                    .executeUpdate();
            insertUserForTest(baseUser(LOCAL_ADMIN_ID, "dev-admin@example.test", Role.ADMIN));
            entityManager.flush();
            oidcProviderConfigurationService.saveProvider(OidcProviderConfiguration.builder()
                    .name("pocketid")
                    .displayName("Pocket ID From Database")
                    .enabled(true)
                    .clientId("database-client")
                    .clientSecret("database-secret")
                    .discoveryUrl("https://database-idp.example/.well-known/openid-configuration")
                    .scopes("openid profile email")
                    .metadataValid(true)
                    .build(), LOCAL_ADMIN_ID);
        });
    }

    @Test
    void importSettingsBackupCreatesDisabledOverrideForEnvironmentProviderAlreadyLoadedFromDatabase() {
        AdminSettingsBackupDto backup = AdminSettingsBackupDto.builder()
                .schemaVersion(AdminSettingsBackupDto.CURRENT_SCHEMA_VERSION)
                .settings(List.of())
                .oidcProviders(List.of())
                .customGeocodingProviders(List.of())
                .build();

        AdminSettingsImportResult result = settingsBackupService.importBackup(backup, LOCAL_ADMIN_ID);

        assertThat(result.getOidcProvidersRemoved()).isEqualTo(1);
        assertThat(result.getOidcEnvironmentOverridesCreated()).isEqualTo(1);

        QuarkusTransaction.requiringNew().run(() -> {
            OidcProviderEntity override = entityManager.find(OidcProviderEntity.class, "pocketid");

            assertThat(override).isNotNull();
            assertThat(override.getEnabled()).isFalse();
            assertThat(override.getClientId()).isEqualTo("env-client");
            assertThat(override.getMetadataValid()).isFalse();
            assertThat(override.getMetadataCachedAt()).isNull();
        });
    }

    @Test
    void restoreFullBackupDeletesLocalConflictsAndPreservesBackupUserIds() throws Exception {
        UUID localUserId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        QuarkusTransaction.requiringNew().run(() -> {
            insertUserForTest(baseUser(localUserId, "local-user@example.test", Role.USER));
            entityManager.flush();
            entityManager.createNativeQuery("""
                            INSERT INTO user_api_tokens (
                                id, user_id, name, token_hash, token_prefix, token_suffix, created_at
                            ) VALUES (
                                :tokenId, :userId, 'local-token', :tokenHash, 'gp_local', 'local01', NOW()
                            )
                            """)
                    .setParameter("tokenId", BACKUP_API_TOKEN_ID)
                    .setParameter("userId", localUserId)
                    .setParameter("tokenHash", BACKUP_API_TOKEN_HASH)
                    .executeUpdate();
            entityManager.createNativeQuery("""
                            INSERT INTO user_oidc_connections (
                                user_id, provider_name, external_user_id, display_name, linked_at
                            ) VALUES (
                                :userId, 'pocketid', 'external-1', 'Local Link', NOW()
                            )
                            """)
                    .setParameter("userId", localUserId)
                    .executeUpdate();
        });

        fullBackupService.restoreFullBackup(minimalFullBackup(backupUsers(), true), LOCAL_ADMIN_ID);

        QuarkusTransaction.requiringNew().run(() -> {
            assertThat(entityManager.find(UserEntity.class, LOCAL_ADMIN_ID)).isNull();
            assertThat(entityManager.find(UserEntity.class, localUserId)).isNull();

            UserEntity restoredUser = entityManager.find(UserEntity.class, BACKUP_USER_ID);
            assertThat(restoredUser).isNotNull();
            assertThat(restoredUser.getEmail()).isEqualTo("server1-user@example.test");
            assertThat(restoredUser.getPasswordHash()).isEqualTo("server1-hash");
            assertThat(restoredUser.getMapRenderMode()).isEqualTo(MapRenderMode.VECTOR);
            assertThat(restoredUser.getDistanceUnit()).isEqualTo(DistanceUnit.KILOMETERS);
            assertThat(restoredUser.getTemperatureUnit()).isEqualTo(TemperatureUnit.CELSIUS);

            Object[] tokenRow = (Object[]) entityManager.createNativeQuery("""
                            SELECT user_id, revoked_by
                            FROM user_api_tokens
                            WHERE id = :tokenId
                            """)
                    .setParameter("tokenId", BACKUP_API_TOKEN_ID)
                    .getSingleResult();
            assertThat(UUID.fromString(tokenRow[0].toString())).isEqualTo(BACKUP_USER_ID);
            assertThat(UUID.fromString(tokenRow[1].toString())).isEqualTo(SECOND_BACKUP_USER_ID);

            Object[] oidcRow = (Object[]) entityManager.createNativeQuery("""
                            SELECT user_id, display_name
                            FROM user_oidc_connections
                            WHERE provider_name = 'pocketid' AND external_user_id = 'external-1'
                            """)
                    .getSingleResult();
            assertThat(UUID.fromString(oidcRow[0].toString())).isEqualTo(BACKUP_USER_ID);
            assertThat(oidcRow[1]).isEqualTo("Server One Link");

            OidcProviderEntity override = entityManager.find(OidcProviderEntity.class, "pocketid");
            assertThat(override).isNotNull();
            assertThat(override.getEnabled()).isFalse();
            assertThat(override.getCreatedBy()).isNull();
        });
    }

    @Test
    void restoreFullBackupRestoresReferenceDatasetsWhenBackupSchemaIncludesThem() throws Exception {
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("""
                            INSERT INTO geonames_country (
                                iso_alpha2, country_name
                            ) VALUES ('ZZ', 'Local Only Country')
                            """)
                    .executeUpdate();
            entityManager.createNativeQuery("""
                            INSERT INTO geonames_city (
                                geonameid, name, latitude, longitude
                            ) VALUES (999999, 'Local Only City', 0, 0)
                            """)
                    .executeUpdate();
            entityManager.createNativeQuery("""
                            INSERT INTO water_surface_polygons (source, source_id, name, water_type, geom)
                            VALUES ('local', 'local-water', 'Local Water', 'lake',
                                    ST_Multi(ST_GeomFromText('POLYGON((0 0,0 1,1 1,1 0,0 0))', 4326)))
                            """)
                    .executeUpdate();
        });

        fullBackupService.restoreFullBackup(minimalFullBackup(backupUsers(), true), LOCAL_ADMIN_ID);

        QuarkusTransaction.requiringNew().run(() -> {
            assertThat(singleString("SELECT country_name FROM geonames_country WHERE iso_alpha2 = 'UA'"))
                    .isEqualTo("Ukraine");
            assertThat(countRows("SELECT COUNT(*) FROM geonames_country WHERE iso_alpha2 = 'ZZ'"))
                    .isZero();
            assertThat(singleString("SELECT name FROM geonames_city WHERE geonameid = 703448"))
                    .isEqualTo("Kyiv");
            assertThat(countRows("SELECT COUNT(*) FROM geonames_city WHERE geonameid = 999999"))
                    .isZero();
            assertThat(countRows("SELECT COUNT(*) FROM water_surface_polygons WHERE source_id = 'backup-water'"))
                    .isEqualTo(1);
            assertThat(countRows("SELECT COUNT(*) FROM water_surface_polygons WHERE source_id = 'local-water'"))
                    .isZero();
            assertThat(singleString("SELECT status FROM water_dataset_state WHERE dataset_key = 'water_surfaces_v1'"))
                    .isEqualTo("READY");
        });
    }

    @Test
    void writeFullBackupIncludesReferenceDatasetEntries() throws Exception {
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("TRUNCATE TABLE users RESTART IDENTITY CASCADE").executeUpdate();
            insertBackupReferenceRows();
        });

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(output)) {
            fullBackupService.writeFullBackup(zos);
        }

        Map<String, byte[]> entries = readZipEntries(output.toByteArray());
        AdminBackupManifestDto manifest = objectMapper.readValue(entries.get("manifest.json"), AdminBackupManifestDto.class);

        assertThat(manifest.getSchemaVersion()).isEqualTo(2);
        assertThat(manifest.getReferenceEntries()).containsExactlyInAnyOrder(
                "reference/geonames-country.csv",
                "reference/geonames-city.csv",
                "reference/geo-dataset-metadata.csv",
                "reference/water-surface-polygons.csv",
                "reference/water-dataset-state.csv"
        );
        assertThat(entries).containsKeys(
                "reference/geonames-country.csv",
                "reference/geonames-city.csv",
                "reference/geo-dataset-metadata.csv",
                "reference/water-surface-polygons.csv",
                "reference/water-dataset-state.csv"
        );
        assertThat(new String(entries.get("reference/geonames-country.csv"), StandardCharsets.UTF_8))
                .contains("Ukraine");
    }

    @Test
    void failureAfterDestructivePhaseLeavesBlockedRestoreStatusAndRetryCanComplete() throws Exception {
        byte[] invalidBackup = minimalFullBackup(backupUsers(), true, "not-a-hex-value");

        assertThat(maintenanceService.tryStartRestore("restore", "invalid.zip")).isTrue();
        assertThatThrownBy(() -> fullBackupService.restoreFullBackup(invalidBackup, LOCAL_ADMIN_ID))
                .isInstanceOf(RuntimeException.class);
        maintenanceService.finishFailure("invalid water geometry");

        AdminBackupStatusDto failed = maintenanceService.getStatus();
        assertThat(failed.getStatus()).isEqualTo("failed");
        assertThat(failed.isRestoreRequired()).isTrue();
        assertThat(failed.isEnvironmentBlocked()).isTrue();

        byte[] validBackup = minimalFullBackup(backupUsers(), true);
        assertThat(maintenanceService.tryStartRestore("restore", "valid.zip")).isTrue();
        fullBackupService.restoreFullBackup(validBackup, LOCAL_ADMIN_ID);
        maintenanceService.finishSuccess("valid.zip", (long) validBackup.length);

        AdminBackupStatusDto completed = maintenanceService.getStatus();
        assertThat(completed.getStatus()).isEqualTo("completed");
        assertThat(completed.isRestoreRequired()).isFalse();
        assertThat(completed.isEnvironmentBlocked()).isFalse();
    }

    private List<AdminFullBackupUsersDto.UserBackupDto> backupUsers() {
        return List.of(
                AdminFullBackupUsersDto.UserBackupDto.builder()
                        .id(BACKUP_USER_ID)
                        .email("server1-user@example.test")
                        .emailVerified(true)
                        .passwordHash("server1-hash")
                        .fullName("Server One User")
                        .createdAt(Instant.parse("2025-12-01T00:00:00Z"))
                        .updatedAt(Instant.parse("2025-12-02T00:00:00Z"))
                        .active(true)
                        .role(Role.ADMIN)
                        .timezone("UTC")
                        .timeFormat("24h")
                        .apiTokens(List.of(AdminFullBackupUsersDto.ApiTokenBackupDto.builder()
                                .id(BACKUP_API_TOKEN_ID)
                                .name("server1-token")
                                .tokenHash(BACKUP_API_TOKEN_HASH)
                                .tokenPrefix("gp_test")
                                .tokenSuffix("abcdef")
                                .createdAt(Instant.parse("2025-12-03T00:00:00Z"))
                                .revokedAt(Instant.parse("2025-12-04T00:00:00Z"))
                                .revokedBy(SECOND_BACKUP_USER_ID)
                                .build()))
                        .oidcConnections(List.of(AdminFullBackupUsersDto.OidcConnectionBackupDto.builder()
                                .providerName("pocketid")
                                .externalUserId("external-1")
                                .displayName("Server One Link")
                                .linkedAt(Instant.parse("2025-12-05T00:00:00Z"))
                                .build()))
                        .build(),
                AdminFullBackupUsersDto.UserBackupDto.builder()
                        .id(SECOND_BACKUP_USER_ID)
                        .email("server1-admin@example.test")
                        .emailVerified(true)
                        .passwordHash("server1-admin-hash")
                        .fullName("Server One Admin")
                        .createdAt(Instant.parse("2025-12-01T00:00:00Z"))
                        .updatedAt(Instant.parse("2025-12-02T00:00:00Z"))
                        .active(true)
                        .role(Role.ADMIN)
                        .timezone("UTC")
                        .timeFormat("24h")
                        .apiTokens(List.of())
                        .oidcConnections(List.of())
                        .build()
        );
    }

    private byte[] minimalFullBackup(List<AdminFullBackupUsersDto.UserBackupDto> backupUsers,
                                     boolean includeReferenceDatasets) throws Exception {
        return minimalFullBackup(backupUsers, includeReferenceDatasets, waterPolygonHex());
    }

    private byte[] minimalFullBackup(List<AdminFullBackupUsersDto.UserBackupDto> backupUsers,
                                     boolean includeReferenceDatasets,
                                     String waterPolygonHex) throws Exception {
        AdminSettingsBackupDto settings = AdminSettingsBackupDto.builder()
                .schemaVersion(AdminSettingsBackupDto.CURRENT_SCHEMA_VERSION)
                .settings(List.of())
                .oidcProviders(List.of())
                .customGeocodingProviders(List.of())
                .build();
        AdminFullBackupUsersDto users = AdminFullBackupUsersDto.builder()
                .dataType("users")
                .exportDate(Instant.parse("2026-01-01T00:00:00Z"))
                .users(backupUsers)
                .build();
        AdminBackupManifestDto manifest = AdminBackupManifestDto.builder()
                .schemaVersion(includeReferenceDatasets ? AdminBackupManifestDto.CURRENT_SCHEMA_VERSION : 1)
                .backupType("geopulse-full")
                .appVersion("test")
                .backupStartedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .exportedAt(Instant.parse("2026-01-01T00:00:01Z"))
                .userCount(backupUsers.size())
                .userIds(backupUsers.stream().map(AdminFullBackupUsersDto.UserBackupDto::getId).toList())
                .referenceEntries(includeReferenceDatasets ? List.of(
                        "reference/geonames-country.csv",
                        "reference/geonames-city.csv",
                        "reference/geo-dataset-metadata.csv",
                        "reference/water-surface-polygons.csv",
                        "reference/water-dataset-state.csv"
                ) : null)
                .warnings(List.of())
                .build();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(output)) {
            addJson(zos, "manifest.json", manifest);
            addJson(zos, "admin-settings.json", settings);
            addJson(zos, "users/users.json", users);
            if (includeReferenceDatasets) {
                addReferenceEntries(zos, waterPolygonHex);
            }
        }
        return output.toByteArray();
    }

    private void addReferenceEntries(ZipOutputStream zos, String waterPolygonHex) throws Exception {
        addEntry(zos, "reference/geonames-country.csv", """
                iso_alpha2,iso_alpha3,iso_numeric,fips_code,country_name,capital,area_sq_km,population,continent,tld,currency_code,currency_name,phone,postal_code_format,postal_code_regex,languages,geonameid,neighbors,equivalent_fips_code
                UA,UKR,804,UP,Ukraine,Kyiv,603700,41000000,EU,.ua,UAH,Hryvnia,380,,,,690791,PL,
                """.getBytes(StandardCharsets.UTF_8));
        addEntry(zos, "reference/geonames-city.csv", """
                geonameid,name,asciiname,alternatenames,latitude,longitude,feature_class,feature_code,country_code,cc2,admin1_code,admin2_code,admin3_code,admin4_code,population,elevation,dem,timezone,modification_date
                703448,Kyiv,Kyiv,,50.45,30.52,P,PPLC,UA,,12,,,,2800000,,,Europe/Kyiv,2026-01-01
                """.getBytes(StandardCharsets.UTF_8));
        addEntry(zos, "reference/geo-dataset-metadata.csv", """
                dataset_name,source_url,source_version,license,attribution,feature_count,imported_at
                water_surface_polygons:geopulse_water_surfaces_v1,backup-source,v1,license,attribution,1,2026-01-01T00:00:00Z
                """.getBytes(StandardCharsets.UTF_8));
        addEntry(zos, "reference/water-surface-polygons.csv", (
                "id,source,source_id,name,water_type,geom_ewkb_hex\n"
                        + "1,backup,backup-water,Backup Lake,lake," + waterPolygonHex + "\n"
        ).getBytes(StandardCharsets.UTF_8));
        addEntry(zos, "reference/water-dataset-state.csv", """
                dataset_key,status,phase,progress_percentage,downloaded_bytes,total_bytes,artifact_url,local_path,sha256,dataset_version,feature_count,error_code,error_message,started_at,completed_at,updated_at
                water_surfaces_v1,READY,done,100,,,,,abc,v1,1,,,2026-01-01T00:00:00Z,2026-01-01T00:00:00Z,2026-01-01T00:00:00Z
                """.getBytes(StandardCharsets.UTF_8));
    }

    private void insertBackupReferenceRows() {
        entityManager.createNativeQuery("""
                        INSERT INTO geonames_country (
                            iso_alpha2, iso_alpha3, iso_numeric, fips_code, country_name, capital,
                            area_sq_km, population, continent, tld, currency_code, currency_name,
                            phone, geonameid
                        ) VALUES (
                            'UA', 'UKR', 804, 'UP', 'Ukraine', 'Kyiv',
                            603700, 41000000, 'EU', '.ua', 'UAH', 'Hryvnia',
                            '380', 690791
                        )
                        """)
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO geonames_city (
                            geonameid, name, asciiname, latitude, longitude, feature_class,
                            feature_code, country_code, admin1_code, population, timezone, modification_date
                        ) VALUES (
                            703448, 'Kyiv', 'Kyiv', 50.45, 30.52, 'P',
                            'PPLC', 'UA', '12', 2800000, 'Europe/Kyiv', DATE '2026-01-01'
                        )
                        """)
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO geo_dataset_metadata (
                            dataset_name, source_url, source_version, license, attribution, feature_count, imported_at
                        ) VALUES (
                            'water_surface_polygons:geopulse_water_surfaces_v1',
                            'backup-source', 'v1', 'license', 'attribution', 1, TIMESTAMPTZ '2026-01-01T00:00:00Z'
                        )
                        """)
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO water_surface_polygons (id, source, source_id, name, water_type, geom)
                        VALUES (1, 'backup', 'backup-water', 'Backup Lake', 'lake',
                                ST_Multi(ST_GeomFromText('POLYGON((0 0,0 1,1 1,1 0,0 0))', 4326)))
                        """)
                .executeUpdate();
        entityManager.createNativeQuery("""
                        INSERT INTO water_dataset_state (
                            dataset_key, status, phase, progress_percentage, sha256, dataset_version,
                            feature_count, started_at, completed_at, updated_at
                        ) VALUES (
                            'water_surfaces_v1', 'READY', 'done', 100, 'abc', 'v1',
                            1, TIMESTAMPTZ '2026-01-01T00:00:00Z',
                            TIMESTAMPTZ '2026-01-01T00:00:00Z',
                            TIMESTAMPTZ '2026-01-01T00:00:00Z'
                        )
                        """)
                .executeUpdate();
    }

    private String waterPolygonHex() {
        return QuarkusTransaction.requiringNew().call(() -> singleString("""
                SELECT encode(ST_AsEWKB(ST_Multi(ST_GeomFromText('POLYGON((0 0,0 1,1 1,1 0,0 0))', 4326))), 'hex')
                """));
    }

    private UserEntity baseUser(UUID id, String email, Role role) {
        UserEntity user = UserEntity.builder()
                .email(email)
                .emailVerified(true)
                .passwordHash("hash-" + email)
                .fullName(email)
                .createdAt(Instant.parse("2025-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2025-01-02T00:00:00Z"))
                .isActive(true)
                .role(role)
                .timezone("UTC")
                .mapRenderMode(MapRenderMode.VECTOR)
                .distanceUnit(DistanceUnit.KILOMETERS)
                .temperatureUnit(TemperatureUnit.CELSIUS)
                .timeFormat("24h")
                .build();
        user.setId(id);
        return user;
    }

    private void insertUserForTest(UserEntity user) {
        entityManager.createNativeQuery("""
                        INSERT INTO users (
                            id, email, emailVerified, password_hash, full_name, created_at, updated_at,
                            is_active, role, timezone, timeline_status, map_render_mode, distance_unit,
                            temperature_unit, time_format, coverage_enabled,
                            timeline_display_auto_show_trip_replay_controls,
                            timeline_display_map_matching_enabled
                        ) VALUES (
                            :id, :email, :emailVerified, :passwordHash, :fullName, :createdAt, :updatedAt,
                            :active, :role, :timezone, 'IDLE', :mapRenderMode, :distanceUnit,
                            :temperatureUnit, :timeFormat, :coverageEnabled,
                            :timelineDisplayAutoShowTripReplayControls,
                            :timelineDisplayMapMatchingEnabled
                        )
                        """)
                .setParameter("id", user.getId())
                .setParameter("email", user.getEmail())
                .setParameter("emailVerified", user.isEmailVerified())
                .setParameter("passwordHash", user.getPasswordHash())
                .setParameter("fullName", user.getFullName())
                .setParameter("createdAt", user.getCreatedAt())
                .setParameter("updatedAt", user.getUpdatedAt())
                .setParameter("active", user.isActive())
                .setParameter("role", user.getRole().name())
                .setParameter("timezone", user.getTimezone())
                .setParameter("mapRenderMode", user.getMapRenderMode().name())
                .setParameter("distanceUnit", user.getDistanceUnit().name())
                .setParameter("temperatureUnit", user.getTemperatureUnit().name())
                .setParameter("timeFormat", user.getTimeFormat())
                .setParameter("coverageEnabled", user.isCoverageEnabled())
                .setParameter("timelineDisplayAutoShowTripReplayControls", user.getTimelineDisplayAutoShowTripReplayControls())
                .setParameter("timelineDisplayMapMatchingEnabled", user.getTimelineDisplayMapMatchingEnabled())
                .executeUpdate();
    }

    private String singleString(String sql) {
        Object value = entityManager.createNativeQuery(sql).getSingleResult();
        return value == null ? null : value.toString();
    }

    private long countRows(String sql) {
        Number value = (Number) entityManager.createNativeQuery(sql).getSingleResult();
        return value == null ? 0 : value.longValue();
    }

    private Map<String, byte[]> readZipEntries(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zis.readAllBytes());
                }
                zis.closeEntry();
            }
        }
        return entries;
    }

    private void addJson(ZipOutputStream zos, String entryName, Object value) throws Exception {
        addEntry(zos, entryName, objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8));
    }

    private void addEntry(ZipOutputStream zos, String entryName, byte[] content) throws Exception {
        zos.putNextEntry(new ZipEntry(entryName));
        zos.write(content);
        zos.closeEntry();
    }

    public static class EnvironmentOidcProviderProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "geopulse.oidc.provider.pocketid.enabled", "true",
                    "geopulse.oidc.provider.pocketid.name", "Pocket ID From Environment",
                    "geopulse.oidc.provider.pocketid.client-id", "env-client",
                    "geopulse.oidc.provider.pocketid.client-secret", "env-secret",
                    "geopulse.oidc.provider.pocketid.discovery-url",
                    "https://env-idp.example/.well-known/openid-configuration"
            );
        }
    }
}
