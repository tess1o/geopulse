package org.github.tess1o.geopulse.admin.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.auth.model.AuthResponse;
import org.github.tess1o.geopulse.auth.service.AuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@TestProfile(AdminBootstrapAdminEmailIntegrationTest.AdminEmailProfile.class)
@SerializedDatabaseTest
class AdminBootstrapAdminEmailIntegrationTest extends AbstractAdminBootstrapIntegrationTest {

    @Inject
    AuthenticationService authenticationService;

    @Inject
    SecurePasswordUtils securePasswordUtils;

    @Test
    void freshDatabaseWithConfiguredAdminEmailDoesNotPromoteNonMatchingFirstUser() {
        UserEntity firstUser = registerUser("admin-email-non-matching-first");

        assertEquals(Role.USER, firstUser.getRole());
        assertEquals(0L, adminCount());
    }

    @Test
    void freshDatabaseWithConfiguredAdminEmailPromotesMatchingRegisteredUser() {
        UserEntity adminUser = userService.registerUser(CONFIGURED_ADMIN_EMAIL, "password", "Owner", "UTC");

        assertEquals(Role.ADMIN, adminUser.getRole());
        assertEquals(1L, adminCount());
    }

    @Test
    @Transactional
    void existingDatabaseWithConfiguredAdminEmailPromotesMatchingUserOnly() {
        String otherEmail = TestIds.uniqueEmail("admin-email-existing-other");
        existingUser(otherEmail, Role.USER);
        existingUser(CONFIGURED_ADMIN_EMAIL, Role.USER);

        adminBootstrapService.bootstrapExistingUsers();

        assertEquals(Role.ADMIN, findByEmail(CONFIGURED_ADMIN_EMAIL).orElseThrow().getRole());
        assertEquals(Role.USER, findByEmail(otherEmail).orElseThrow().getRole());
        assertEquals(1L, adminCount());
    }

    @Test
    @Transactional
    void existingConfiguredAdminEmailUserIsPromotedOnLogin() {
        String password = "password";
        existingUser(CONFIGURED_ADMIN_EMAIL, Role.USER, securePasswordUtils.hashPassword(password));

        AuthResponse response = authenticationService.authenticate(CONFIGURED_ADMIN_EMAIL, password);

        assertEquals(Role.ADMIN.name(), response.getRole());
        assertEquals(Role.ADMIN, findByEmail(CONFIGURED_ADMIN_EMAIL).orElseThrow().getRole());
        assertEquals(1L, adminCount());
    }

    public static class AdminEmailProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "geopulse.admin.email", CONFIGURED_ADMIN_EMAIL,
                    "geopulse.admin.first-user-admin.enabled", "true"
            );
        }
    }
}
