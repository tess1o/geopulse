package org.github.tess1o.geopulse.admin.rest;

import io.vertx.core.http.HttpServerRequest;
import org.github.tess1o.geopulse.admin.dto.BulkUpdateRequest;
import org.github.tess1o.geopulse.admin.dto.UpdateSettingRequest;
import org.github.tess1o.geopulse.admin.service.AuditLogService;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.auth.service.CurrentUserService;
import org.github.tess1o.geopulse.shared.api.ApiErrorCode;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Validation failures on setting updates must surface as HTTP 400 (INVALID_ADMIN_SETTINGS),
 * not as an unmapped 500.
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class AdminSettingsResourceLogLevelTest {

    private static final String KEY = "system.logging.application-level";
    private static final String INVALID_MESSAGE =
            "Setting system.logging.application-level must be ERROR, WARN, INFO, or DEBUG; use reset to inherit";

    @Mock
    SystemSettingsService settingsService;
    @Mock
    AuditLogService auditLogService;
    @Mock
    CurrentUserService currentUserService;
    @Mock
    HttpServerRequest httpRequest;

    AdminSettingsResource resource;
    UUID adminId;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        resource = new AdminSettingsResource();
        resource.settingsService = settingsService;
        resource.auditLogService = auditLogService;
        resource.currentUserService = currentUserService;
        resource.httpRequest = httpRequest;
        when(currentUserService.getCurrentUserId()).thenReturn(adminId);
    }

    @Test
    void invalidLevelIsRejectedAsBadRequest() {
        doThrow(new IllegalArgumentException(INVALID_MESSAGE))
                .when(settingsService).setValue(KEY, "VERBOSE", adminId);

        assertThatThrownBy(() -> resource.updateSetting(KEY, request("VERBOSE")))
                .isInstanceOf(GeoPulseException.class)
                .satisfies(exception -> assertThat(((GeoPulseException) exception).code())
                        .isEqualTo(ApiErrorCode.INVALID_ADMIN_SETTINGS));

        verifyNoInteractions(auditLogService);
    }

    @Test
    void validLevelIsStoredAndAuditedWithoutValues() {
        assertThatCode(() -> resource.updateSetting(KEY, request("DEBUG")))
                .doesNotThrowAnyException();

        verify(settingsService).setValue(KEY, "DEBUG", adminId);
        verify(auditLogService).logAction(any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void invalidLevelInBulkUpdateIsRejectedAsBadRequest() {
        doThrow(new IllegalArgumentException(INVALID_MESSAGE))
                .when(settingsService).setValue(KEY, "VERBOSE", adminId);

        BulkUpdateRequest bulkRequest = new BulkUpdateRequest();
        bulkRequest.setSettings(List.of(request("VERBOSE")));

        assertThatThrownBy(() -> resource.bulkUpdateSettings(bulkRequest))
                .isInstanceOf(GeoPulseException.class)
                .satisfies(exception -> assertThat(((GeoPulseException) exception).code())
                        .isEqualTo(ApiErrorCode.INVALID_ADMIN_SETTINGS));
    }

    private static UpdateSettingRequest request(String value) {
        UpdateSettingRequest request = new UpdateSettingRequest();
        request.setKey(KEY);
        request.setValue(value);
        return request;
    }
}
