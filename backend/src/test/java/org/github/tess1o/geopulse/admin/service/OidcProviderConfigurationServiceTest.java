package org.github.tess1o.geopulse.admin.service;

import org.github.tess1o.geopulse.admin.model.OidcProviderEntity;
import org.github.tess1o.geopulse.admin.repository.OidcProviderRepository;
import org.github.tess1o.geopulse.ai.service.AIEncryptionService;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OidcProviderConfigurationService.
 * Tests DB-first with environment fallback pattern.
 */
@ExtendWith(MockitoExtension.class)
class OidcProviderConfigurationServiceTest {

    @Mock
    private OidcProviderRepository repository;

    @Mock
    private AIEncryptionService encryptionService;

    @InjectMocks
    private OidcProviderConfigurationService service;

    private UUID testAdminId;
    private OidcProviderEntity testEntity;
    private OidcProviderConfiguration testConfig;

    @BeforeEach
    void setUp() {
        testAdminId = UUID.randomUUID();

        // Create test entity
        testEntity = OidcProviderEntity.builder()
                .name("google")
                .displayName("Google")
                .enabled(true)
                .clientId("test-client-id")
                .clientSecretEncrypted("encrypted-secret")
                .clientSecretKeyId("v1")
                .discoveryUrl("https://accounts.google.com/.well-known/openid-configuration")
                .icon("pi pi-google")
                .scopes("openid profile email")
                .metadataValid(true)
                .createdAt(Instant.now())
                .build();

        // Create test configuration
        testConfig = OidcProviderConfiguration.builder()
                .name("google")
                .displayName("Google")
                .enabled(true)
                .clientId("test-client-id")
                .clientSecret("plain-secret")
                .discoveryUrl("https://accounts.google.com/.well-known/openid-configuration")
                .icon("pi pi-google")
                .scopes("openid profile email")
                .metadataValid(false)
                .build();
    }

    @Test
    void testLoadAllProviders_ReturnsDbProviders() {
        // Given: Providers exist in database
        when(repository.listAll()).thenReturn(List.of(testEntity));
        when(encryptionService.decrypt("encrypted-secret", "v1")).thenReturn("plain-secret");

        // When: Loading all providers
        List<OidcProviderConfiguration> providers = service.loadAllProviders();

        // Then: DB providers are returned
        assertNotNull(providers);
        assertFalse(providers.isEmpty());

        OidcProviderConfiguration provider = providers.stream()
                .filter(p -> p.getName().equals("google"))
                .findFirst()
                .orElse(null);

        assertNotNull(provider);
        assertEquals("Google", provider.getDisplayName());
        assertEquals("test-client-id", provider.getClientId());
        assertEquals("plain-secret", provider.getClientSecret());
        assertTrue(provider.isEnabled());

        verify(repository, times(1)).listAll();
        verify(encryptionService, times(1)).decrypt("encrypted-secret", "v1");
    }

    @Test
    void testGetProviderByName_FromDatabase() {
        // Given: Provider exists in database
        when(repository.findByName("google")).thenReturn(Optional.of(testEntity));
        when(encryptionService.decrypt("encrypted-secret", "v1")).thenReturn("plain-secret");

        // When: Getting provider by name
        Optional<OidcProviderConfiguration> result = service.getProviderByName("google");

        // Then: Provider is returned from DB
        assertTrue(result.isPresent());
        OidcProviderConfiguration provider = result.get();
        assertEquals("google", provider.getName());
        assertEquals("Google", provider.getDisplayName());
        assertEquals("plain-secret", provider.getClientSecret());

        verify(repository, times(1)).findByName("google");
        verify(encryptionService, times(1)).decrypt("encrypted-secret", "v1");
    }

    @Test
    void testGetProviderByName_NotFound() {
        // Given: Provider does not exist
        when(repository.findByName("nonexistent")).thenReturn(Optional.empty());

        // When: Getting provider by name
        Optional<OidcProviderConfiguration> result = service.getProviderByName("nonexistent");

        // Then: Empty optional is returned
        assertFalse(result.isPresent());

        verify(repository, times(1)).findByName("nonexistent");
        verifyNoInteractions(encryptionService);
    }

    @Test
    void testSaveProvider_CreatesNewProvider() {
        // Given: Provider does not exist
        when(repository.findByName("google")).thenReturn(Optional.empty());
        when(encryptionService.encrypt("plain-secret")).thenReturn("encrypted-secret");
        when(encryptionService.getCurrentKeyId()).thenReturn("v1");
        when(encryptionService.decrypt("encrypted-secret", "v1")).thenReturn("plain-secret");
        doNothing().when(repository).persist(any(OidcProviderEntity.class));

        // When: Saving provider
        OidcProviderConfiguration saved = service.saveProvider(testConfig, testAdminId);

        // Then: Provider is created
        assertNotNull(saved);
        assertEquals("google", saved.getName());

        verify(repository, times(1)).findByName("google");
        verify(encryptionService, times(1)).encrypt("plain-secret");
        verify(encryptionService, times(1)).getCurrentKeyId();
        verify(repository, times(1)).persist(any(OidcProviderEntity.class));
    }

    @Test
    void testSaveProvider_UpdatesExistingProvider() {
        // Given: Provider already exists
        when(repository.findByName("google")).thenReturn(Optional.of(testEntity));
        when(encryptionService.encrypt("new-secret")).thenReturn("encrypted-new-secret");
        when(encryptionService.getCurrentKeyId()).thenReturn("v1");
        when(encryptionService.decrypt("encrypted-new-secret", "v1")).thenReturn("new-secret");
        doNothing().when(repository).persist(any(OidcProviderEntity.class));

        // When: Saving provider with updated secret
        OidcProviderConfiguration updated = testConfig.toBuilder()
                .clientSecret("new-secret")
                .build();
        OidcProviderConfiguration saved = service.saveProvider(updated, testAdminId);

        // Then: Provider is updated
        assertNotNull(saved);
        assertEquals("google", saved.getName());

        verify(repository, times(1)).findByName("google");
        verify(encryptionService, times(1)).encrypt("new-secret");
        verify(encryptionService, times(1)).getCurrentKeyId();
        verify(repository, times(1)).persist(any(OidcProviderEntity.class));
    }

    @Test
    void testUpdateClientSecret() {
        // Given: Provider exists
        when(repository.findByName("google")).thenReturn(Optional.of(testEntity));
        when(encryptionService.encrypt("new-secret")).thenReturn("encrypted-new-secret");
        when(encryptionService.getCurrentKeyId()).thenReturn("v1");
        doNothing().when(repository).persist(any(OidcProviderEntity.class));

        // When: Updating client secret
        service.updateClientSecret("google", "new-secret", testAdminId);

        // Then: Secret is encrypted and persisted
        verify(repository, times(1)).findByName("google");
        verify(encryptionService, times(1)).encrypt("new-secret");
        verify(encryptionService, times(1)).getCurrentKeyId();
        verify(repository, times(1)).persist(any(OidcProviderEntity.class));
    }

    @Test
    void testUpdateClientSecret_ProviderNotFound() {
        // Given: Provider does not exist
        when(repository.findByName("nonexistent")).thenReturn(Optional.empty());

        // When/Then: Exception is thrown
        assertThrows(IllegalArgumentException.class, () ->
                service.updateClientSecret("nonexistent", "new-secret", testAdminId)
        );

        verify(repository, times(1)).findByName("nonexistent");
        verifyNoInteractions(encryptionService);
    }

    @Test
    void testDeleteProvider() {
        // Given: Provider exists
        doNothing().when(repository).deleteByName("google");

        // When: Deleting provider
        service.deleteProvider("google");

        // Then: Provider is deleted from repository
        verify(repository, times(1)).deleteByName("google");
    }

    @Test
    void testExistsInDatabase_True() {
        // Given: Provider exists in DB
        when(repository.existsByName("google")).thenReturn(true);

        // When: Checking existence
        boolean exists = service.existsInDatabase("google");

        // Then: Returns true
        assertTrue(exists);
        verify(repository, times(1)).existsByName("google");
    }

    @Test
    void testExistsInDatabase_False() {
        // Given: Provider does not exist in DB
        when(repository.existsByName("nonexistent")).thenReturn(false);

        // When: Checking existence
        boolean exists = service.existsInDatabase("nonexistent");

        // Then: Returns false
        assertFalse(exists);
        verify(repository, times(1)).existsByName("nonexistent");
    }

    @Test
    void testInvalidateMetadata() {
        // Given: Provider exists
        when(repository.findByName("google")).thenReturn(Optional.of(testEntity));
        doNothing().when(repository).persist(any(OidcProviderEntity.class));

        // When: Invalidating metadata
        service.invalidateMetadata("google");

        // Then: Metadata is invalidated
        verify(repository, times(1)).findByName("google");
        verify(repository, times(1)).persist(any(OidcProviderEntity.class));
    }

    @Test
    void testUpdateMetadata() {
        // Given: Provider exists
        when(repository.findByName("google")).thenReturn(Optional.of(testEntity));
        doNothing().when(repository).persist(any(OidcProviderEntity.class));

        // When: Updating metadata
        OidcProviderConfiguration updatedConfig = testConfig.toBuilder()
                .authorizationEndpoint("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenEndpoint("https://oauth2.googleapis.com/token")
                .metadataValid(true)
                .metadataCachedAt(Instant.now())
                .build();

        service.updateMetadata("google", updatedConfig);

        // Then: Metadata is updated
        verify(repository, times(1)).findByName("google");
        verify(repository, times(1)).persist(any(OidcProviderEntity.class));
    }
}
