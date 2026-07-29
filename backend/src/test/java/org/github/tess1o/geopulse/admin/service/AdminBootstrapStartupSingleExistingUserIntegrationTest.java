package org.github.tess1o.geopulse.admin.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(
        value = PostgisTestResource.class,
        initArgs = @ResourceArg(
                name = PostgisTestResource.DATABASE_NAME_ARG,
                value = "gp_test_admin_bootstrap_startup_single"
        ),
        restrictToAnnotatedClass = true
)
@TestProfile(AdminBootstrapStartupSingleExistingUserIntegrationTest.StartupSingleExistingUserProfile.class)
@SerializedDatabaseTest
class AdminBootstrapStartupSingleExistingUserIntegrationTest {

    static final String STARTUP_SINGLE_USER_EMAIL = "startup-single-user@example.com";

    @Inject
    UserRepository userRepository;

    @Test
    void startupPromotesExactlyOneExistingUserWhenNoAdminExists() {
        assertEquals(Role.ADMIN, userRepository.findByEmailIgnoreCase(STARTUP_SINGLE_USER_EMAIL).orElseThrow().getRole());
        assertEquals(1L, userRepository.count("role", Role.ADMIN));
    }

    public static class StartupSingleExistingUserProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "geopulse.admin.email", "",
                    "geopulse.admin.first-user-admin.enabled", "true",
                    "quarkus.flyway.locations", "db/migration,db/admin-bootstrap-startup-single"
            );
        }
    }
}
