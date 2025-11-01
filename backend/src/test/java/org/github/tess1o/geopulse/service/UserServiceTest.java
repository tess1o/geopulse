package org.github.tess1o.geopulse.service;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.CleanupHelper;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.user.model.UpdateProfileRequest;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.repository.UserRepository;
import org.github.tess1o.geopulse.user.service.SecurePasswordUtils;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.transaction.Transactional;


import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(PostgisTestResource.class)
public class UserServiceTest {

    @Inject
    UserService userService;

    @Inject
    UserRepository userRepository;

    @Inject
    SecurePasswordUtils passwordUtils;

    @Inject
    CleanupHelper cleanupHelper;

    @BeforeEach
    @Transactional
    public void setup() {
        cleanupHelper.cleanupAll();
    }

    @Test
    public void testUserRegistration() {
        Instant startOfTheTest = Instant.now();
        UserEntity user = userService.registerUser("email@test.com", "test", "test", "Europe/Kyiv");
        assertEquals(1, userRepository.count());
        assertEquals("email@test.com", user.getEmail());
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
        UserEntity user = userService.registerUser("test@avatar.com", "password", "Test User", "Europe/Kyiv");

        // Test valid avatar paths
        String[] validPaths = {
                "/avatars/avatar1.png",
                "/avatars/avatar5.png",
                "/avatars/avatar10.png",
                "/avatars/avatar20.png"
        };

        for (String validPath : validPaths) {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setUserId(user.getId());
            request.setFullName("Test User");
            request.setAvatar(validPath);

            // Should not throw exception
            assertDoesNotThrow(() -> userService.updateProfile(request));

            // Verify avatar was set
            UserEntity updatedUser = userRepository.findById(user.getId());
            assertEquals(validPath, updatedUser.getAvatar());
        }
    }

    @Test
    @Transactional
    public void testInvalidAvatarPaths() {
        // Create a test user
        UserEntity user = userService.registerUser("test@avatar.com", "password", "Test User", "Europe/Kyiv");

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
                "javascript:alert(1)",         // XSS attempt
                "/avatars/script.js",          // Script file
                "avatar1.png",                 // Missing leading slash
                ""                             // Empty string (should be allowed)
        };

        for (String invalidPath : invalidPaths) {
            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setUserId(user.getId());
            request.setFullName("Test User");
            request.setAvatar(invalidPath);

            if (invalidPath.isEmpty()) {
                // Empty string should be allowed (removes avatar)
                assertDoesNotThrow(() -> userService.updateProfile(request));
            } else {
                // Should throw IllegalArgumentException
                assertThrows(IllegalArgumentException.class,
                        () -> userService.updateProfile(request),
                        "Expected exception for invalid path: " + invalidPath);
            }
        }
    }

    @Test
    @Transactional
    public void testNullAvatarPath() {
        // Create a test user
        UserEntity user = userService.registerUser("test@avatar.com", "password", "Test User", "Europe/Kyiv");

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUserId(user.getId());
        request.setFullName("Test User");
        request.setAvatar(null); // null should be allowed

        // Should not throw exception
        assertDoesNotThrow(() -> userService.updateProfile(request));
    }
}
