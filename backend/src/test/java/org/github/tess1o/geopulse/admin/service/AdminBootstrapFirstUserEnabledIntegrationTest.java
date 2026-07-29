package org.github.tess1o.geopulse.admin.service;

import io.quarkus.arc.ClientProxy;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.auth.oidc.dto.OidcUserInfo;
import org.github.tess1o.geopulse.auth.oidc.repository.UserOidcConnectionRepository;
import org.github.tess1o.geopulse.auth.oidc.service.OidcAuthenticationService;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@TestProfile(AdminBootstrapFirstUserEnabledIntegrationTest.FirstUserEnabledProfile.class)
@SerializedDatabaseTest
class AdminBootstrapFirstUserEnabledIntegrationTest extends AbstractAdminBootstrapIntegrationTest {

    @Inject
    OidcAuthenticationService oidcAuthenticationService;

    @Inject
    UserOidcConnectionRepository connectionRepository;

    @Test
    void freshDatabasePromotesFirstRegisteredPasswordUserOnly() {
        UserEntity firstUser = registerUser("first-user-admin-password-first");
        UserEntity secondUser = registerUser("first-user-admin-password-second");

        assertEquals(Role.ADMIN, firstUser.getRole());
        assertEquals(Role.USER, secondUser.getRole());
        assertEquals(1L, adminCount());
    }

    @Test
    @Transactional
    void freshDatabasePromotesOidcCreatedFirstUser() throws Exception {
        UserEntity user = createOidcUser(TestIds.uniqueEmail("first-user-admin-oidc"));

        assertEquals(Role.ADMIN, user.getRole());
        assertEquals(1L, adminCount());
        assertEquals(1, connectionRepository.findByUserId(user.getId()).size());
    }

    @Test
    @Transactional
    void existingDatabaseWithExactlyOneUserAndNoAdminPromotesThatUser() {
        String email = TestIds.uniqueEmail("first-user-admin-existing-one");
        existingUser(email, Role.USER);

        adminBootstrapService.bootstrapExistingUsers();

        assertEquals(Role.ADMIN, findByEmail(email).orElseThrow().getRole());
        assertEquals(1L, adminCount());
    }

    @Test
    @Transactional
    void existingDatabaseWithMultipleUsersAndNoAdminDoesNotGuess() {
        String firstEmail = TestIds.uniqueEmail("first-user-admin-existing-many-a");
        String secondEmail = TestIds.uniqueEmail("first-user-admin-existing-many-b");
        existingUser(firstEmail, Role.USER);
        existingUser(secondEmail, Role.USER);

        adminBootstrapService.bootstrapExistingUsers();

        assertEquals(Role.USER, findByEmail(firstEmail).orElseThrow().getRole());
        assertEquals(Role.USER, findByEmail(secondEmail).orElseThrow().getRole());
        assertEquals(0L, adminCount());
    }

    private UserEntity createOidcUser(String email) throws Exception {
        OidcUserInfo userInfo = OidcUserInfo.builder()
                .subject(TestIds.uniqueValue("first-user-admin-oidc-subject"))
                .email(email)
                .emailVerified(true)
                .name("OIDC First User")
                .picture("https://example.com/avatar.jpg")
                .build();

        Method method = OidcAuthenticationService.class.getDeclaredMethod(
                "createNewUserWithOidcConnection",
                OidcUserInfo.class,
                String.class
        );
        method.setAccessible(true);
        OidcAuthenticationService oidcService = ClientProxy.unwrap(oidcAuthenticationService);
        return (UserEntity) method.invoke(oidcService, userInfo, "google");
    }

    public static class FirstUserEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "geopulse.admin.email", "",
                    "geopulse.admin.first-user-admin.enabled", "true"
            );
        }
    }
}
