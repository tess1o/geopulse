package org.github.tess1o.geopulse.admin;

import org.github.tess1o.geopulse.admin.deletion.UserDeletionPhase;
import org.github.tess1o.geopulse.admin.deletion.UserDeletionResponse;
import org.github.tess1o.geopulse.admin.deletion.UserDeletionStatus;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.admin.service.LogLevelService;
import org.github.tess1o.geopulse.admin.backup.RestorePhase;
import org.github.tess1o.geopulse.admin.backup.RestoreOperationState;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.admin.dto.*;
import org.github.tess1o.geopulse.admin.dto.backup.*;
import org.github.tess1o.geopulse.admin.model.*;

@RegisterForReflection(
        targets = {
                RestoreOperationState.class,
                RestorePhase.class,
                LogLevelService.LoggingStatus.class,
                LogLevelService.Source.class,
                // DTOs
                AdminSettingsBackupDto.class,
                AdminSettingsBackupDto.SettingBackupDto.class,
                AdminSettingsBackupDto.OidcProviderBackupDto.class,
                AdminSettingsBackupDto.CustomGeocodingProviderBackupDto.class,
                AdminSettingsImportResult.class,
                AdminBackupConfigDto.class,
                AdminBackupPasswordRequest.class,
                AdminBackupFileDto.class,
                AdminBackupStatusDto.class,
                MapMatchingProviderTestResponse.class,
                MapMatchingRebuildResponse.class,
                UserAISettings.class,
                RestoreLocalBackupRequest.class,
                RestoreAcceptedResponse.class,
                BulkUpdateRequest.class,
                CreateOidcProviderRequest.class,
                OidcProviderResponse.class,
                OidcProviderResponse.ProviderSource.class,
                TestOidcProviderResponse.class,
                UpdateOidcProviderRequest.class,
                UserDetailsResponse.class,
                ResetPasswordResponse.class,
                UpdateSettingRequest.class,
                UpdateUserStatusRequest.class,
                UpdateUserRoleRequest.class,
                UserListResponse.class,
                UserDeletionResponse.class,
                SettingInfo.class,
                SettingDefinition.class,
                AuditLogResponse.class,
                AdminDashboardResponse.class,
                AdminDashboardResponse.MetricsEnabled.class,
                AdminDashboardResponse.Health.class,
                AdminDashboardResponse.BackupHealth.class,
                AdminDashboardResponse.IngestionHealth.class,
                AdminDashboardResponse.MapMatchingHealth.class,
                AdminDashboardResponse.SecurityHealth.class,
                AdminDashboardResponse.SecurityWarningCode.class,
                InvitationBaseUrlResponse.class,
                PanoramaxTestResponse.class,
                PoiTestResponse.class,
                SettingResetResponse.class,
                AdminBackupCreatedResponse.class,
                MaintenanceStatusDto.class,
                // Invitation DTOs
                InvitationResponse.class,
                InvitationResponse.AdminUserInfo.class,
                CreateInvitationRequest.class,
                CreateInvitationResponse.class,
                InvitationRegisterRequest.class,
                ValidateInvitationResponse.class,
                // Entities
                OidcProviderEntity.class,
                SystemSettingsEntity.class,
                AuditLogEntity.class,
                UserInvitationEntity.class,
                // Enums
                Role.class,
                ActionType.class,
                TargetType.class,
                ValueType.class,
                SettingsCategory.class,
                UserDeletionStatus.class,
                UserDeletionPhase.Stage.class,
                InvitationStatus.class
        }
)
public class AdminNativeConfig {
}
