package org.github.tess1o.geopulse.admin.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.core.Response;
import org.github.tess1o.geopulse.admin.dto.AdminSettingsBackupDto;
import org.github.tess1o.geopulse.admin.model.ActionType;
import org.github.tess1o.geopulse.admin.model.TargetType;
import org.github.tess1o.geopulse.admin.service.AdminSettingsBackupService;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class AdminSettingsBackupResourceTest {

    @Mock
    AdminSettingsBackupService backupService;
    @Mock
    AuditLogService auditLogService;
    @Mock
    CurrentUserService currentUserService;
    @Mock
    HttpServerRequest httpRequest;

    AdminSettingsBackupResource resource;
    UUID adminId;

    @BeforeEach
    void setUp() {
        resource = new AdminSettingsBackupResource();
        resource.backupService = backupService;
        resource.auditLogService = auditLogService;
        resource.currentUserService = currentUserService;
        resource.objectMapper = new ObjectMapper().findAndRegisterModules();
        resource.httpRequest = httpRequest;
        adminId = UUID.randomUUID();
    }

    @Test
    void exportSettingsBackupAuditsCountsWithoutSecretValues() {
        when(currentUserService.getCurrentUserId()).thenReturn(adminId);
        when(backupService.exportBackup()).thenReturn(AdminSettingsBackupDto.builder()
                .schemaVersion(AdminSettingsBackupDto.CURRENT_SCHEMA_VERSION)
                .settings(List.of(AdminSettingsBackupDto.SettingBackupDto.builder()
                        .key("geocoding.googlemaps.api-key")
                        .value("plain-secret")
                        .build()))
                .oidcProviders(List.of(AdminSettingsBackupDto.OidcProviderBackupDto.builder()
                        .name("keycloak")
                        .clientSecret("oidc-secret")
                        .build()))
                .customGeocodingProviders(List.of(AdminSettingsBackupDto.CustomGeocodingProviderBackupDto.builder()
                        .name("local-photon")
                        .headers(Map.of("X-Api-Key", "header-secret"))
                        .build()))
                .build());

        Response response = resource.exportSettingsBackup("203.0.113.7", null);

        assertThat(response.getStatus()).isEqualTo(200);
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).logAction(
                eq(adminId),
                eq(ActionType.ADMIN_SETTINGS_EXPORTED),
                eq(TargetType.SETTING),
                eq("admin-settings-backup"),
                detailsCaptor.capture(),
                eq("203.0.113.7"));
        assertThat(detailsCaptor.getValue().toString())
                .doesNotContain("plain-secret", "oidc-secret", "header-secret")
                .contains("settings=1", "oidcProviders=1", "customGeocodingProviders=1");
    }
}
