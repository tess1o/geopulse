package org.github.tess1o.geopulse.admin.rest;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.impl.SocketAddressImpl;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.github.tess1o.geopulse.admin.dto.CreateOidcProviderRequest;
import org.github.tess1o.geopulse.admin.dto.OidcProviderResponse;
import org.github.tess1o.geopulse.admin.dto.UpdateOidcProviderRequest;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.admin.service.OidcProviderConfigurationService;
import org.github.tess1o.geopulse.auth.oidc.model.OidcProviderConfiguration;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
/**
 * Integration tests for AdminOidcProviderResource.
 * Tests REST endpoint behavior for OIDC provider management.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
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
        List<OidcProviderResponse> providers = resource.getAllProviders();
        // Then: Success response with providers
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
        OidcProviderResponse provider = resource.getProvider("google");
        // Then: Success response with provider
        assertEquals("google", provider.getName());
        assertEquals("Google", provider.getDisplayName());
        verify(configurationService, times(1)).getProviderByName("google");
    }
    @Test
    void testGetProvider_NotFound_Returns404() {
        // Given: Provider does not exist
        when(configurationService.getProviderByName("nonexistent")).thenReturn(Optional.empty());
        // When: Getting provider
        GeoPulseException problem = assertThrows(GeoPulseException.class, () -> resource.getProvider("nonexistent"));
        // Then: Not found response
        assertEquals(404, problem.code().statusCode());
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
        var response = resource.createProvider(createRequest);
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
        GeoPulseException problem = assertThrows(GeoPulseException.class,
                () -> resource.createProvider(createRequest));
        // Then: Conflict response
        assertEquals(409, problem.code().statusCode());
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
        OidcProviderResponse response = resource.updateProvider("google", updateRequest);
        // Then: Success response
        assertNotNull(response);
        verify(configurationService, times(1)).getProviderByName("google");
        verify(configurationService, times(1)).saveProvider(any(OidcProviderConfiguration.class), eq(testAdminId));
        verify(auditLogService, times(1)).logAction(any(), any(), any(), any(), any(), any());
    }
    @Test
    void testUpdateProvider_NotFound_Returns404() {
        // Given: Provider does not exist
        when(configurationService.getProviderByName("nonexistent")).thenReturn(Optional.empty());
        // When: Updating provider
        GeoPulseException problem = assertThrows(GeoPulseException.class,
                () -> resource.updateProvider("nonexistent", updateRequest));
        // Then: Not found response
        assertEquals(404, problem.code().statusCode());
        verify(configurationService, times(1)).getProviderByName("nonexistent");
        verify(configurationService, never()).saveProvider(any(), any());
        verifyNoInteractions(auditLogService);
    }
    @Test
    void testDeleteProvider_FromDatabase_Success() {
        // Given: Provider exists in database only
        when(configurationService.existsInDatabase("google")).thenReturn(true);
        when(configurationService.getProviderByName("google")).thenReturn(Optional.of(testProvider));
        doNothing().when(configurationService).deleteProvider("google");
        doNothing().when(auditLogService).logAction(any(), any(), any(), any(), any(), any());
        // When: Deleting provider
        resource.deleteProvider("google");
        // Then: Success response
        verify(configurationService, times(1)).existsInDatabase("google");
        verify(configurationService, times(1)).deleteProvider("google");
        verify(auditLogService, times(1)).logAction(any(), any(), any(), any(), any(), any());
    }
    @Test
    void testDeleteProvider_EnvironmentOnly_Returns400() {
        // Given: Provider exists in environment only (not in DB)
        when(configurationService.existsInDatabase("google")).thenReturn(false);
        // When: Deleting provider
        GeoPulseException problem = assertThrows(GeoPulseException.class,
                () -> resource.deleteProvider("google"));
        // Then: Bad request response
        assertEquals(400, problem.code().statusCode());
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
        OidcProviderResponse response = resource.resetProvider("google");
        // Then: Success response
        assertNotNull(response);
        verify(configurationService, atLeastOnce()).isFromEnvironment("google");
        verify(configurationService, times(1)).deleteProvider("google");
        verify(auditLogService, times(1)).logAction(any(), any(), any(), any(), any(), any());
    }
    @Test
    void testResetProvider_NotInEnvironment_Returns400() {
        // Given: Provider does not exist in environment
        when(configurationService.isFromEnvironment("nonexistent")).thenReturn(false);
        // When: Resetting provider
        GeoPulseException problem = assertThrows(GeoPulseException.class,
                () -> resource.resetProvider("nonexistent"));
        // Then: Bad request response
        assertEquals(400, problem.code().statusCode());
        verify(configurationService, times(1)).isFromEnvironment("nonexistent");
        verify(configurationService, never()).deleteProvider(any());
        verifyNoInteractions(auditLogService);
    }
}
