package org.github.tess1o.geopulse.admin.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// No @TestProfile: the config this used to override (an empty geopulse.admin.email and
// geopulse.admin.first-user-admin.enabled=false) is already what src/test/resources/application.properties
// provides. Quarkus caches the running application per profile class rather than per config value, so the
// redundant profile cost a full application boot and a fresh database for no change in behaviour.
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
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
}
