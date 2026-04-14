package org.github.tess1o.geopulse.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UpdateProfileRequest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
public class UserServiceTest {

    @Inject
    UserService userService;

    @Inject
    UserRepository userRepository;

    @Inject
    SecurePasswordUtils passwordUtils;


    @Test
    public void testUserRegistration() {
        Instant startOfTheTest = Instant.now();
        String email = TestIds.uniqueEmail("user-service-registration");
        UserEntity user = userService.registerUser(email, "test", "test", "Europe/Kyiv");
        assertEquals(1, userRepository.count("email = ?1", email));
        assertEquals(email, user.getEmail());
        assertEquals("test", user.getFullName());
        assertTrue(user.getCreatedAt().isBefore(Instant.now()));
        assertTrue(user.getCreatedAt().isAfter(startOfTheTest));
        assertNull(user.getUpdatedAt());
        assertTrue(passwordUtils.isPasswordValid("test", user.getPasswordHash()));
    }
    @Test
    @Transactional
    public void testValidAvatarPaths() {
        // Create a test user
        UserEntity user = userService.registerUser(
                TestIds.uniqueEmail("user-service-avatar-valid"), "password", "Test User", "Europe/Kyiv");
        // Test valid avatar paths
        String[] validPaths = {
                "/avatars/avatar1.png",
                "/avatars/avatar5.png",
                "/avatars/avatar10.png",
                "/avatars/avatar20.png",
                "/api/users/" + user.getId() + "/avatar"
        };
        for (String validPath : validPaths) {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setFullName("Test User");
            request.setAvatar(validPath);
            // Should not throw exception
            assertDoesNotThrow(() -> userService.updateProfile(user.getId(), request));
            // Verify avatar was set
            UserEntity updatedUser = userRepository.findById(user.getId());
            assertEquals(validPath, updatedUser.getAvatar());
        }
    }

    @Test
    @Transactional
    public void testInvalidAvatarPaths() {
        // Create a test user
        UserEntity user = userService.registerUser(
                TestIds.uniqueEmail("user-service-avatar-invalid"), "password", "Test User", "Europe/Kyiv");
        // Test invalid avatar paths
        String[] invalidPaths = {
                "/avatars/avatar0.png",        // Below range
                "/avatars/avatar21.png",       // Above range
                "/avatars/avatar1.jpg",        // Wrong extension
                "/avatars/avatar-1.png",       // Wrong format
                "/images/avatar1.png",         // Wrong directory
                "../avatars/avatar1.png",      // Path traversal
                "/avatars//avatar1.png",       // Double slash
                "/avatars/avatar1.png.exe",    // Suspicious extension
                "/api/users/not-a-uuid/avatar",// Invalid custom avatar URL
                "/api/users//avatar",          // Invalid path
                "javascript:alert(1)",         // XSS attempt
                "/avatars/script.js",          // Script file
                "avatar1.png",                 // Missing leading slash
                ""                             // Empty string (should be allowed)
        };
        for (String invalidPath : invalidPaths) {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setFullName("Test User");
            request.setAvatar(invalidPath);
            if (invalidPath.isEmpty()) {
                // Empty string should be allowed (removes avatar)
                assertDoesNotThrow(() -> userService.updateProfile(user.getId(),request));
            } else {
                // Should throw IllegalArgumentException
                assertThrows(IllegalArgumentException.class,
                        () -> userService.updateProfile(user.getId(),request),
                        "Expected exception for invalid path: " + invalidPath);
            }
        }
    }

    @Test
    @Transactional
    public void testNullAvatarPath() {
        // Create a test user
        UserEntity user = userService.registerUser(
                TestIds.uniqueEmail("user-service-avatar-null"), "password", "Test User", "Europe/Kyiv");
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Test User");
        request.setAvatar(null); // null should be allowed
        // Should not throw exception
        assertDoesNotThrow(() -> userService.updateProfile(user.getId(),request));
    }

    @Test
    @Transactional
    public void testValidTimeFormatValues() {
        UserEntity user = userService.registerUser(
                TestIds.uniqueEmail("user-service-time-format-valid"), "password", "Test User", "Europe/Kyiv");

        UpdateProfileRequest updateTo12h = new UpdateProfileRequest();
        updateTo12h.setFullName("Test User");
        updateTo12h.setTimeFormat("12h");
        assertDoesNotThrow(() -> userService.updateProfile(user.getId(), updateTo12h));
        assertEquals("12h", userRepository.findById(user.getId()).getTimeFormat());

        UpdateProfileRequest updateTo24h = new UpdateProfileRequest();
        updateTo24h.setFullName("Test User");
        updateTo24h.setTimeFormat("24h");
        assertDoesNotThrow(() -> userService.updateProfile(user.getId(), updateTo24h));
        assertEquals("24h", userRepository.findById(user.getId()).getTimeFormat());
    }

    @Test
    @Transactional
    public void testInvalidTimeFormatValues() {
        UserEntity user = userService.registerUser(
                TestIds.uniqueEmail("user-service-time-format-invalid"), "password", "Test User", "Europe/Kyiv");

        UpdateProfileRequest invalidRequest = new UpdateProfileRequest();
        invalidRequest.setFullName("Test User");
        invalidRequest.setTimeFormat("AMPM");

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateProfile(user.getId(), invalidRequest));
    }
}
