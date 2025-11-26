package org.github.tess1o.geopulse.admin.rest;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.impl.SocketAddressImpl;
import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.admin.dto.CreateOidcProviderRequest;
import org.github.tess1o.geopulse.admin.dto.OidcProviderResponse;
import org.github.tess1o.geopulse.admin.dto.UpdateOidcProviderRequest;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.admin.service.OidcProviderConfigurationService;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for AdminOidcProviderResource.
 * Tests REST endpoint behavior for OIDC provider management.
 */
@ExtendWith(MockitoExtension.class)
class AdminOidcProviderResourceTest {

    @Mock
    private OidcProviderConfigurationService configurationService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private AdminOidcProviderResource resource;

    @Mock
    private HttpServerRequest httpServerRequest;

    private UUID testAdminId;
    private OidcProviderConfiguration testProvider;
    private CreateOidcProviderRequest createRequest;
    private UpdateOidcProviderRequest updateRequest;

    @BeforeEach
    void setUp() {
        testAdminId = UUID.randomUUID();

        lenient().when(httpServerRequest.remoteAddress()).thenReturn(new SocketAddressImpl(0, "localhost"));

        // Mock JWT
        lenient().when(currentUserService.getCurrentUserId()).thenReturn(testAdminId);

        // Create test provider
        testProvider = OidcProviderConfiguration.builder()
                .name("google")
                .displayName("Google")
                .enabled(true)
                .clientId("test-client-id")
                .clientSecret("test-secret")
                .discoveryUrl("https://accounts.google.com/.well-known/openid-configuration")
                .icon("pi pi-google")
                .scopes("openid profile email")
                .metadataValid(true)
                .build();

        // Create request objects
        createRequest = CreateOidcProviderRequest.builder()
                .name("keycloak")
                .displayName("Keycloak")
                .enabled(true)
                .clientId("keycloak-client")
                .clientSecret("keycloak-secret")
                .discoveryUrl("https://keycloak.example.com/realms/master/.well-known/openid-configuration")
                .icon("pi pi-shield")
                .scopes("openid profile email")
                .build();

        updateRequest = UpdateOidcProviderRequest.builder()
                .displayName("Google Updated")
                .enabled(true)
                .clientId("updated-client-id")
                .discoveryUrl("https://accounts.google.com/.well-known/openid-configuration")
                .icon("pi pi-google")
                .scopes("openid profile email address")
                .build();
    }

    @Test
    void testGetAllProviders_ReturnsProviderList() {
        // Given: Providers exist
        when(configurationService.loadAllProviders()).thenReturn(List.of(testProvider));
        when(configurationService.existsInDatabase("google")).thenReturn(true);
        when(configurationService.isFromEnvironment("google")).thenReturn(false);

        // When: Getting all providers
        Response response = resource.getAllProviders();

        // Then: Success response with providers
        assertEquals(200, response.getStatus());
        assertNotNull(response.getEntity());

        List<OidcProviderResponse> providers = (List<OidcProviderResponse>) response.getEntity();
        assertEquals(1, providers.size());
        assertEquals("google", providers.get(0).getName());
        assertEquals("Google", providers.get(0).getDisplayName());

        verify(configurationService, times(1)).loadAllProviders();
    }

    @Test
    void testGetProvider_Found_ReturnsProvider() {
        // Given: Provider exists
        when(configurationService.getProviderByName("google")).thenReturn(Optional.of(testProvider));
        when(configurationService.existsInDatabase("google")).thenReturn(true);
        when(configurationService.isFromEnvironment("google")).thenReturn(false);

        // When: Getting provider
        Response response = resource.getProvider("google");

        // Then: Success response with provider
        assertEquals(200, response.getStatus());
        assertNotNull(response.getEntity());

        OidcProviderResponse provider = (OidcProviderResponse) response.getEntity();
        assertEquals("google", provider.getName());
        assertEquals("Google", provider.getDisplayName());

        verify(configurationService, times(1)).getProviderByName("google");
    }

    @Test
    void testGetProvider_NotFound_Returns404() {
        // Given: Provider does not exist
        when(configurationService.getProviderByName("nonexistent")).thenReturn(Optional.empty());

        // When: Getting provider
        Response response = resource.getProvider("nonexistent");

        // Then: Not found response
        assertEquals(404, response.getStatus());

        verify(configurationService, times(1)).getProviderByName("nonexistent");
    }

    @Test
    void testCreateProvider_Success_Returns201() {
        // Given: Provider does not exist
        when(configurationService.getProviderByName("keycloak")).thenReturn(Optional.empty());

        OidcProviderConfiguration savedProvider = OidcProviderConfiguration.builder()
                .name("keycloak")
                .displayName("Keycloak")
                .enabled(true)
                .clientId("keycloak-client")
                .clientSecret("keycloak-secret")
                .discoveryUrl("https://keycloak.example.com/realms/master/.well-known/openid-configuration")
                .icon("pi pi-shield")
                .scopes("openid profile email")
                .metadataValid(false)
                .build();

        when(configurationService.saveProvider(any(OidcProviderConfiguration.class), eq(testAdminId)))
                .thenReturn(savedProvider);
        when(configurationService.existsInDatabase("keycloak")).thenReturn(true);
        when(configurationService.isFromEnvironment("keycloak")).thenReturn(false);

        doNothing().when(auditLogService).logAction(any(), any(), any(), any(), any(), any());

        // When: Creating provider
        Response response = resource.createProvider(createRequest, null, null);

        // Then: Created response
        assertEquals(201, response.getStatus());
        assertNotNull(response.getEntity());

        verify(configurationService, times(1)).getProviderByName("keycloak");
        verify(configurationService, times(1)).saveProvider(any(OidcProviderConfiguration.class), eq(testAdminId));
        verify(auditLogService, times(1)).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testCreateProvider_AlreadyExists_Returns409() {
        // Given: Provider already exists
        when(configurationService.getProviderByName("keycloak")).thenReturn(Optional.of(testProvider));

        // When: Creating provider
        Response response = resource.createProvider(createRequest, null, null);

        // Then: Conflict response
        assertEquals(409, response.getStatus());

        verify(configurationService, times(1)).getProviderByName("keycloak");
        verify(configurationService, never()).saveProvider(any(), any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void testUpdateProvider_Success_Returns200() {
        // Given: Provider exists
        when(configurationService.getProviderByName("google")).thenReturn(Optional.of(testProvider));

        OidcProviderConfiguration updatedProvider = testProvider.toBuilder()
                .displayName("Google Updated")
                .clientId("updated-client-id")
                .scopes("openid profile email address")
                .build();

        when(configurationService.saveProvider(any(OidcProviderConfiguration.class), eq(testAdminId)))
                .thenReturn(updatedProvider);
        when(configurationService.existsInDatabase("google")).thenReturn(true);
        when(configurationService.isFromEnvironment("google")).thenReturn(false);

        doNothing().when(auditLogService).logAction(any(), any(), any(), any(), any(), any());

        // When: Updating provider
        Response response = resource.updateProvider("google", updateRequest, null, null);

        // Then: Success response
        assertEquals(200, response.getStatus());
        assertNotNull(response.getEntity());

        verify(configurationService, times(1)).getProviderByName("google");
        verify(configurationService, times(1)).saveProvider(any(OidcProviderConfiguration.class), eq(testAdminId));
        verify(auditLogService, times(1)).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testUpdateProvider_NotFound_Returns404() {
        // Given: Provider does not exist
        when(configurationService.getProviderByName("nonexistent")).thenReturn(Optional.empty());

        // When: Updating provider
        Response response = resource.updateProvider("nonexistent", updateRequest, null, null);

        // Then: Not found response
        assertEquals(404, response.getStatus());

        verify(configurationService, times(1)).getProviderByName("nonexistent");
        verify(configurationService, never()).saveProvider(any(), any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void testDeleteProvider_FromDatabase_Success() {
        // Given: Provider exists in database only
        when(configurationService.existsInDatabase("google")).thenReturn(true);
        when(configurationService.getProviderByName("google")).thenReturn(Optional.of(testProvider));
        when(configurationService.isFromEnvironment("google")).thenReturn(false);

        doNothing().when(configurationService).deleteProvider("google");
        doNothing().when(auditLogService).logAction(any(), any(), any(), any(), any(), any());

        // When: Deleting provider
        Response response = resource.deleteProvider("google", null, null);

        // Then: Success response
        assertEquals(200, response.getStatus());

        verify(configurationService, times(1)).existsInDatabase("google");
        verify(configurationService, times(1)).deleteProvider("google");
        verify(auditLogService, times(1)).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testDeleteProvider_EnvironmentOnly_Returns400() {
        // Given: Provider exists in environment only (not in DB)
        when(configurationService.existsInDatabase("google")).thenReturn(false);

        // When: Deleting provider
        Response response = resource.deleteProvider("google", null, null);

        // Then: Bad request response
        assertEquals(400, response.getStatus());

        verify(configurationService, times(1)).existsInDatabase("google");
        verify(configurationService, never()).deleteProvider(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void testResetProvider_Success() {
        // Given: Provider exists in environment
        when(configurationService.isFromEnvironment("google")).thenReturn(true);
        when(configurationService.getProviderByName("google")).thenReturn(Optional.of(testProvider));
        when(configurationService.existsInDatabase("google")).thenReturn(false);

        doNothing().when(configurationService).deleteProvider("google");
        doNothing().when(auditLogService).logAction(any(), any(), any(), any(), any(), any());

        // When: Resetting provider
        Response response = resource.resetProvider("google", null, null);

        // Then: Success response
        assertEquals(200, response.getStatus());

        verify(configurationService, atLeastOnce()).isFromEnvironment("google");
        verify(configurationService, times(1)).deleteProvider("google");
        verify(auditLogService, times(1)).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testResetProvider_NotInEnvironment_Returns400() {
        // Given: Provider does not exist in environment
        when(configurationService.isFromEnvironment("nonexistent")).thenReturn(false);

        // When: Resetting provider
        Response response = resource.resetProvider("nonexistent", null, null);

        // Then: Bad request response
        assertEquals(400, response.getStatus());

        verify(configurationService, times(1)).isFromEnvironment("nonexistent");
        verify(configurationService, never()).deleteProvider(any());
        verifyNoInteractions(auditLogService);
    }
}
