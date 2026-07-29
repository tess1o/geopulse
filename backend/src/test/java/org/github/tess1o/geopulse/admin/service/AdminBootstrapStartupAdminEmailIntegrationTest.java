package org.github.tess1o.geopulse.admin.service;

import io.quarkus.test.common.QuarkusTestResource;
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
@QuarkusTestResource(value = PostgisTestResource.class)
@TestProfile(AdminBootstrapStartupAdminEmailIntegrationTest.StartupAdminEmailProfile.class)
@SerializedDatabaseTest
class AdminBootstrapStartupAdminEmailIntegrationTest {

    static final String CONFIGURED_ADMIN_EMAIL = "owner@example.com";
    static final String OTHER_USER_EMAIL = "startup-admin-email-other@example.com";

    @Inject
    UserRepository userRepository;

    @Test
    void startupPromotesConfiguredAdminEmailAmongExistingUsers() {
        assertEquals(Role.ADMIN, userRepository.findByEmailIgnoreCase(CONFIGURED_ADMIN_EMAIL).orElseThrow().getRole());
        assertEquals(Role.USER, userRepository.findByEmailIgnoreCase(OTHER_USER_EMAIL).orElseThrow().getRole());
        assertEquals(1L, userRepository.count("role", Role.ADMIN));
    }

    public static class StartupAdminEmailProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "geopulse.admin.email", CONFIGURED_ADMIN_EMAIL,
                    "geopulse.admin.first-user-admin.enabled", "true",
                    "quarkus.flyway.locations", "db/migration,db/admin-bootstrap-startup-admin-email"
            );
        }
    }
}
