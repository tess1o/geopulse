package org.github.tess1o.geopulse.admin.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@TestProfile(AdminBootstrapFirstUserDisabledIntegrationTest.FirstUserDisabledProfile.class)
@SerializedDatabaseTest
class AdminBootstrapFirstUserDisabledIntegrationTest extends AbstractAdminBootstrapIntegrationTest {

    @Test
    void freshDatabaseKeepsFirstRegisteredUserAsUserWhenFirstUserBootstrapIsDisabled() {
        UserEntity user = registerUser("first-user-admin-disabled");

        assertEquals(Role.USER, user.getRole());
        assertEquals(0L, adminCount());
    }

    @Test
    @Transactional
    void existingDatabaseWithExactlyOneUserDoesNotPromoteWhenFirstUserBootstrapIsDisabled() {
        String email = TestIds.uniqueEmail("first-user-admin-disabled-existing");
        existingUser(email, Role.USER);

        adminBootstrapService.bootstrapExistingUsers();

        assertEquals(Role.USER, findByEmail(email).orElseThrow().getRole());
        assertEquals(0L, adminCount());
    }

    public static class FirstUserDisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "geopulse.admin.email", "",
                    "geopulse.admin.first-user-admin.enabled", "false"
            );
        }
    }
}
