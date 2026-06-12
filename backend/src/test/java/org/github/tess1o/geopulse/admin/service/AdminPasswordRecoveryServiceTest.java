package org.github.tess1o.geopulse.admin.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
public class AdminPasswordRecoveryServiceTest {

    @Inject
    AdminPasswordRecoveryService recoveryService;

    @Inject
    AdminUserService adminUserService;

    @Inject
    UserService userService;

    @Inject
    UserRepository userRepository;

    @Inject
    SecurePasswordUtils passwordUtils;

    @Test
    void resetsExistingAdminPassword() {
        UserEntity admin = createUser(Role.ADMIN, true);

        AdminPasswordRecoveryResult result = recoveryService.resetPassword(
                admin.getEmail(),
                Optional.of("new-password"),
                false,
                true
        );

        UserEntity updated = userRepository.findById(admin.getId());
        assertEquals(admin.getEmail(), result.email());
        assertFalse(result.generatedPassword());
        assertNull(result.temporaryPassword());
        assertFalse(result.promoted());
        assertFalse(result.activated());
        assertTrue(passwordUtils.isPasswordValid("new-password", updated.getPasswordHash()));
    }

    @Test
    void generatesTemporaryPasswordWhenPasswordIsOmitted() {
        UserEntity admin = createUser(Role.ADMIN, true);

        AdminPasswordRecoveryResult result = recoveryService.resetPassword(
                admin.getEmail(),
                Optional.empty(),
                false,
                true
        );

        UserEntity updated = userRepository.findById(admin.getId());
        assertTrue(result.generatedPassword());
        assertNotNull(result.temporaryPassword());
        assertTrue(result.temporaryPassword().length() >= 16);
        assertTrue(passwordUtils.isPasswordValid(result.temporaryPassword(), updated.getPasswordHash()));
    }

    @Test
    void activatesDisabledAdminByDefault() {
        UserEntity admin = createUser(Role.ADMIN, false);

        AdminPasswordRecoveryResult result = recoveryService.resetPassword(
                admin.getEmail(),
                Optional.of("new-password"),
                false,
                true
        );

        assertTrue(result.activated());
        assertTrue(userRepository.findById(admin.getId()).isActive());
    }

    @Test
    void leavesDisabledAdminInactiveWhenRequested() {
        UserEntity admin = createUser(Role.ADMIN, false);

        AdminPasswordRecoveryResult result = recoveryService.resetPassword(
                admin.getEmail(),
                Optional.of("new-password"),
                false,
                false
        );

        assertFalse(result.activated());
        assertFalse(userRepository.findById(admin.getId()).isActive());
    }

    @Test
    void rejectsNonAdminUnlessPromotionIsRequested() {
        UserEntity user = createUser(Role.USER, true);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                recoveryService.resetPassword(user.getEmail(), Optional.of("new-password"), false, true));

        assertTrue(exception.getMessage().contains("--promote"));
    }

    @Test
    void promotesNonAdminWhenRequested() {
        UserEntity user = createUser(Role.USER, true);

        AdminPasswordRecoveryResult result = recoveryService.resetPassword(
                user.getEmail(),
                Optional.of("new-password"),
                true,
                true
        );

        UserEntity updated = userRepository.findById(user.getId());
        assertTrue(result.promoted());
        assertEquals(Role.ADMIN, updated.getRole());
        assertTrue(passwordUtils.isPasswordValid("new-password", updated.getPasswordHash()));
    }

    @Test
    void rejectsShortPassword() {
        UserEntity admin = createUser(Role.ADMIN, true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                recoveryService.resetPassword(admin.getEmail(), Optional.of("short"), false, true));

        assertTrue(exception.getMessage().contains("at least 6 characters"));
    }

    private UserEntity createUser(Role role, boolean active) {
        UserEntity user = userService.registerUser(
                TestIds.uniqueEmail("admin-password-recovery"),
                "old-password",
                "Recovery Test User",
                "UTC"
        );
        userService.updateRole(user.getId(), role);
        adminUserService.setUserStatus(user.getId(), active);
        return user;
    }
}
