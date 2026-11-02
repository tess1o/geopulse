package org.github.tess1o.geopulse.admin.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.dto.AdminSettingsBackupDto;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.AdminTestUsers;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestActor;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP round trip for {@code /api/v1/admin/backups/settings}: a file produced by the export endpoint
 * must be accepted by the import endpoint unchanged, exactly as the UI does it.
 *
 * <p>Regression for issue #644, where a 2.1.1 export always contained
 * {@code system.logging.application-level = "inherit"} and the import rejected that value with
 * {@code INVALID_ADMIN_SETTINGS_BACKUP}.
 */
@QuarkusTest
@QuarkusTestResource(
        value = PostgisTestResource.class,
        initArgs = @ResourceArg(
                name = PostgisTestResource.DATABASE_NAME_ARG,
                value = "gp_test_admin_settings_backup_roundtrip"
        ),
        restrictToAnnotatedClass = true
)
@TestProfile(AdminSettingsBackupRoundTripIntegrationTest.SettingsBackupRoundTripProfile.class)
@SerializedDatabaseTest
class AdminSettingsBackupRoundTripIntegrationTest {

    private static final String SETTINGS_BACKUPS = "/api/v1/admin/backups/settings";
    private static final String EXPORTS = SETTINGS_BACKUPS + "/exports";
    private static final String IMPORTS = SETTINGS_BACKUPS + "/imports";
    private static final String SETTING_KEY_URL = "/api/v1/admin/settings/keys/";
    private static final String LOG_LEVEL_KEY = SystemSettingsService.APPLICATION_LOG_LEVEL_KEY;

    @Inject UserService userService;
    @Inject AuthenticationService authenticationService;
    @Inject SystemSettingsService settingsService;

    private TestActor admin;

    @BeforeEach
    @Transactional
    void setUp() {
        admin = AdminTestUsers.createAdmin(userService, authenticationService, "settings-backup-admin");
    }

    @Test
    void exportedSettingsCanBeImportedBackIntoTheSameInstance() {
        Response exported = authenticated().when().get(EXPORTS);

        assertThat(exported.statusCode()).isEqualTo(200);
        assertThat(exported.jsonPath().getInt("schemaVersion"))
                .isEqualTo(AdminSettingsBackupDto.CURRENT_SCHEMA_VERSION);
        assertThat(exported.jsonPath().getString("scope")).isEqualTo(AdminSettingsBackupDto.BACKUP_SCOPE);
        assertThat(exported.jsonPath().getList("settings")).isNotEmpty();
        byte[] backup = exported.asByteArray();

        // The untouched export describes a deployment without a log level override. Importing it must
        // clear an override present on the target instance rather than reject the file.
        authenticated().contentType(ContentType.JSON)
                .body(Map.of("key", LOG_LEVEL_KEY, "value", "DEBUG"))
                .when().put(SETTING_KEY_URL + LOG_LEVEL_KEY)
                .then().statusCode(204);
        assertThat(settingsService.isDefault(LOG_LEVEL_KEY)).isFalse();

        Response imported = authenticated()
                .multiPart("file", "admin-settings-backup.json", backup, "application/json")
                .when().post(IMPORTS);

        assertThat(imported.statusCode()).isEqualTo(200);
        assertThat(imported.jsonPath().getList("unsupportedSettings", String.class))
                .doesNotContain(LOG_LEVEL_KEY);
        assertThat(imported.jsonPath().getInt("settingsImported")).isPositive();
        assertThat(settingsService.isDefault(LOG_LEVEL_KEY)).isTrue();
    }

    private RequestSpecification authenticated() {
        return given().header("Authorization", "Bearer " + admin.accessToken());
    }

    public static class SettingsBackupRoundTripProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of();
        }
    }
}
