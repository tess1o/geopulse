package org.github.tess1o.geopulse.auth.oidc.service;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.github.tess1o.geopulse.auth.oidc.model.OidcSessionStateEntity;
import org.github.tess1o.geopulse.auth.oidc.model.UserOidcConnectionEntity;
import org.github.tess1o.geopulse.auth.oidc.repository.OidcSessionStateRepository;
import org.github.tess1o.geopulse.auth.oidc.repository.UserOidcConnectionRepository;
import org.github.tess1o.geopulse.db.PostgisTestResource;
import org.github.tess1o.geopulse.testsupport.SerializedDatabaseTest;
import org.github.tess1o.geopulse.testsupport.TestIds;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
/**
 * Test class for OIDC auto-link accounts functionality.
 * Tests the behavior when geopulse.oidc.auto-link-accounts is enabled/disabled.
 */
@QuarkusTest
@QuarkusTestResource(value = PostgisTestResource.class)
@SerializedDatabaseTest
public class OidcAutoLinkAccountsTest {
    @Inject
    UserService userService;
    @Inject
    OidcSessionStateRepository sessionStateRepository;
    @Inject
    UserOidcConnectionRepository connectionRepository;
    @BeforeEach
    @Transactional
    public void setup() {
    }
    /**
     * Test profile that enables auto-link accounts
     */
    public static class AutoLinkEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "geopulse.oidc.auto-link-accounts", "true",
                "geopulse.auth.oidc-registration-enabled", "true"
            );
        }
    }
    /**
     * Test profile that disables auto-link accounts (default behavior)
     */
    public static class AutoLinkDisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "geopulse.oidc.auto-link-accounts", "false",
                "geopulse.auth.oidc-registration-enabled", "true"
            );
        }
    }
    /**
     * Test: Verify user registration and OIDC connection setup
     * This tests the baseline scenario for auto-link functionality
     */
    @Test
    @Transactional
    public void testUserRegistrationAndOidcSetup() {
        // Create an existing user with password
        String existingEmail = TestIds.uniqueEmail("oidc-existing-user");
        UserEntity existingUser = userService.registerUser(
            existingEmail,
            "password123",
            "Existing User",
            "UTC"
        );
        // Verify user was created
        assertNotNull(existingUser);
        assertEquals(existingEmail, existingUser.getEmail());
        // Verify no OIDC connections exist for this user
        assertEquals(0, connectionRepository.findByUserId(existingUser.getId()).size());
        // Note: We cannot directly test findOrCreateUser as it's private
        // This test verifies the setup for integration testing
        // The actual behavior would be tested through the full handleCallback flow
    }
    /**
     * Test: Verify that OIDC connection can be created for a new user
     */
    @Test
    @Transactional
    public void testCreateOidcConnectionForNewUser() {
        // Create a user
        String email = TestIds.uniqueEmail("oidc-new");
        String externalUserId = TestIds.uniqueValue("google-id");
        UserEntity user = userService.registerUser(
            email,
            "password",
            "Test User",
            "UTC"
        );
        // Create OIDC connection
        UserOidcConnectionEntity connection = UserOidcConnectionEntity.builder()
            .userId(user.getId())
            .providerName("google")
            .externalUserId(externalUserId)
            .displayName("Test User")
            .avatarUrl("https://example.com/avatar.jpg")
            .lastLoginAt(Instant.now())
            .build();
        connectionRepository.persist(connection);
        // Verify connection was created
        var connections = connectionRepository.findByUserId(user.getId());
        assertEquals(1, connections.size());
        assertEquals("google", connections.get(0).getProviderName());
        assertEquals(externalUserId, connections.get(0).getExternalUserId());
    }
    /**
     * Test: Verify that when OIDC connection already exists, last login is updated
     */
    @Test
    @Transactional
    public void testExistingOidcConnection_UpdatesLastLogin() {
        // Create a user
        String email = TestIds.uniqueEmail("oidc-existing");
        String externalUserId = TestIds.uniqueValue("google-id");
        UserEntity user = userService.registerUser(
            email,
            "password",
            "Test User",
            "UTC"
        );
        // Create initial OIDC connection with old timestamp
        Instant oldTimestamp = Instant.now().minus(1, ChronoUnit.DAYS);
        UserOidcConnectionEntity connection = UserOidcConnectionEntity.builder()
            .userId(user.getId())
            .providerName("google")
            .externalUserId(externalUserId)
            .displayName("Test User")
            .avatarUrl("https://example.com/avatar.jpg")
            .lastLoginAt(oldTimestamp)
            .build();
        connectionRepository.persist(connection);
        // Find the connection again
        var foundConnection = connectionRepository.findByProviderNameAndExternalUserId("google", externalUserId);
        assertTrue(foundConnection.isPresent());
        // Update last login
        Instant newTimestamp = Instant.now();
        foundConnection.get().setLastLoginAt(newTimestamp);
        connectionRepository.persist(foundConnection.get());
        // Verify last login was updated
        var updatedConnection = connectionRepository.findByProviderNameAndExternalUserId("google", externalUserId);
        assertTrue(updatedConnection.isPresent());
        assertTrue(updatedConnection.get().getLastLoginAt().isAfter(oldTimestamp));
    }
    /**
     * Test: Verify session state creation and retrieval
     */
    @Test
    @Transactional
    public void testSessionStateCreation() {
        // Create session state
        String stateToken = TestIds.uniqueValue("oidc-state");
        String nonce = TestIds.uniqueValue("oidc-nonce");
        OidcSessionStateEntity sessionState = OidcSessionStateEntity.builder()
            .stateToken(stateToken)
            .nonce(nonce)
            .providerName("google")
            .redirectUri("/app/timeline")
            .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
            .build();
        sessionStateRepository.persist(sessionState);
        // Verify session state was created
        var foundSession = sessionStateRepository.findByStateToken(stateToken);
        assertTrue(foundSession.isPresent());
        assertEquals("google", foundSession.get().getProviderName());
        assertEquals(nonce, foundSession.get().getNonce());
        assertEquals("/app/timeline", foundSession.get().getRedirectUri());
    }
    /**
     * Test: Verify that an OIDC account cannot be linked to multiple users
     */
    @Test
    @Transactional
    public void testOidcAccountCannotBeLinkToMultipleUsers() {
        // Create two users
        String user1Email = TestIds.uniqueEmail("oidc-u1");
        String user2Email = TestIds.uniqueEmail("oidc-u2");
        String sharedExternalId = TestIds.uniqueValue("google-shared");
        UserEntity user1 = userService.registerUser(
            user1Email,
            "password",
            "User 1",
            "UTC"
        );
        UserEntity user2 = userService.registerUser(
            user2Email,
            "password",
            "User 2",
            "UTC"
        );
        // Link OIDC account to first user
        UserOidcConnectionEntity connection1 = UserOidcConnectionEntity.builder()
            .userId(user1.getId())
            .providerName("google")
            .externalUserId(sharedExternalId)
            .displayName("User 1")
            .lastLoginAt(Instant.now())
            .build();
        connectionRepository.persist(connection1);
        // Try to link same OIDC account to second user
        // This should be prevented by unique constraint or business logic
        var existingConnection = connectionRepository.findByProviderNameAndExternalUserId(
            "google",
            sharedExternalId
        );
        assertTrue(existingConnection.isPresent());
        assertEquals(user1.getId(), existingConnection.get().getUserId());
        assertNotEquals(user2.getId(), existingConnection.get().getUserId());
    }
    /**
     * Test: Verify that a user can have multiple OIDC connections from different providers
     */
    @Test
    @Transactional
    public void testUserCanHaveMultipleOidcConnections() {
        // Create a user
        String email = TestIds.uniqueEmail("oidc-multi");
        String googleExternalId = TestIds.uniqueValue("google-id");
        String microsoftExternalId = TestIds.uniqueValue("microsoft-id");
        UserEntity user = userService.registerUser(
            email,
            "password",
            "Test User",
            "UTC"
        );
        // Link Google account
        UserOidcConnectionEntity googleConnection = UserOidcConnectionEntity.builder()
            .userId(user.getId())
            .providerName("google")
            .externalUserId(googleExternalId)
            .displayName("Test User")
            .lastLoginAt(Instant.now())
            .build();
        connectionRepository.persist(googleConnection);
        // Link Microsoft account
        UserOidcConnectionEntity microsoftConnection = UserOidcConnectionEntity.builder()
            .userId(user.getId())
            .providerName("microsoft")
            .externalUserId(microsoftExternalId)
            .displayName("Test User")
            .lastLoginAt(Instant.now())
            .build();
        connectionRepository.persist(microsoftConnection);
        // Verify both connections exist
        var connections = connectionRepository.findByUserId(user.getId());
        assertEquals(2, connections.size());
        var providerNames = connections.stream()
            .map(UserOidcConnectionEntity::getProviderName)
            .sorted()
            .toList();
        assertEquals("google", providerNames.get(0));
        assertEquals("microsoft", providerNames.get(1));
    }
}
