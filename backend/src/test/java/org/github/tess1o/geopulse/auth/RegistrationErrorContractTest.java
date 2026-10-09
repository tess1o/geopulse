package org.github.tess1o.geopulse.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.admin.model.UserInvitationEntity;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.admin.service.UserInvitationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.github.tess1o.geopulse.testsupport.ApiProblemAssertions.assertProblemEnvelope;

/**
 * Every way a registration can fail answers with its own code, so the client can tell the user what
 * actually went wrong. The regression this guards: a disabled registration and a duplicate email both
 * surfaced as USER_REGISTRATION_CONFLICT, and an invitation registration for a taken email as a bare
 * INVALID_INVITATION "Bad Request".
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
class RegistrationErrorContractTest {

    private static final String PASSWORD_REGISTRATION_SETTING = "auth.password-registration.enabled";

    @Inject UserService userService;
    @Inject UserInvitationService invitationService;
    @Inject SystemSettingsService settingsService;

    String existingEmail;
    UserEntity admin;

    @BeforeEach
    void setUp() {
        existingEmail = TestIds.uniqueEmail("registration-error-existing");
        userService.registerUser(existingEmail, "password123", "Existing User", "UTC");
        admin = userService.registerUser(TestIds.uniqueEmail("registration-error-admin"),
                "password123", "Invitation Admin", "UTC");
    }

    @Test
    void duplicateEmailIsAConflict() {
        Response response = register(existingEmail);

        assertProblemEnvelope(response, 409, "USER_REGISTRATION_CONFLICT");
        assertThat(response.jsonPath().getString("detail")).isEqualTo("An account with this email already exists");
    }

    @Test
    void disabledPasswordRegistrationIsNotAConflict() {
        settingsService.setValue(PASSWORD_REGISTRATION_SETTING, "false", null);
        try {
            Response response = register(TestIds.uniqueEmail("registration-error-disabled"));

            assertProblemEnvelope(response, 403, "PASSWORD_REGISTRATION_DISABLED");
        } finally {
            settingsService.resetToDefault(PASSWORD_REGISTRATION_SETTING);
        }
    }

    @Test
    void invitationRegistrationWithATakenEmailIsAConflict() {
        UserInvitationEntity invitation = invitationService.createInvitation(admin.getId(), null, null);

        Response response = registerWithInvitation(invitation.getToken(), existingEmail);

        assertProblemEnvelope(response, 409, "USER_REGISTRATION_CONFLICT");
    }

    @Test
    void revokedInvitationCarriesItsStatus() {
        UserInvitationEntity invitation = invitationService.createInvitation(admin.getId(), null, null);
        invitationService.revokeInvitation(invitation.getId(), admin.getId(), null);

        Response response = registerWithInvitation(invitation.getToken(),
                TestIds.uniqueEmail("registration-error-revoked"));

        assertProblemEnvelope(response, 400, "INVALID_INVITATION");
        assertThat(response.jsonPath().getString("parameters.status")).isEqualTo("REVOKED");
    }

    @Test
    void unknownInvitationTokenIsNotFound() {
        Response response = registerWithInvitation(TestIds.uniqueValue("missing-invitation"),
                TestIds.uniqueEmail("registration-error-missing"));

        assertProblemEnvelope(response, 404, "INVITATION_NOT_FOUND");
    }

    private Response register(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "password123", "fullName", "New User", "timezone", "UTC"))
                .when().post("/api/v1/registrations");
    }

    private Response registerWithInvitation(String token, String email) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "password123", "fullName", "Invited User", "timezone", "UTC"))
                .when().post("/api/v1/registration-invitations/" + token + "/registrations");
    }
}
