package org.github.tess1o.geopulse.admin;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.ai.model.UserAISettings;
import org.github.tess1o.geopulse.admin.dto.*;
import org.github.tess1o.geopulse.admin.dto.backup.*;
import org.github.tess1o.geopulse.admin.model.*;

@RegisterForReflection(
        targets = {
                // DTOs
                AdminSettingsBackupDto.class,
                AdminSettingsBackupDto.SettingBackupDto.class,
                AdminSettingsBackupDto.OidcProviderBackupDto.class,
                AdminSettingsBackupDto.CustomGeocodingProviderBackupDto.class,
                AdminSettingsImportResult.class,
                AdminBackupConfigDto.class,
                AdminBackupFileDto.class,
                AdminBackupManifestDto.class,
                AdminBackupStatusDto.class,
                AdminFullBackupUsersDto.class,
                AdminFullBackupUsersDto.UserBackupDto.class,
                AdminFullBackupUsersDto.ApiTokenBackupDto.class,
                AdminFullBackupUsersDto.OidcConnectionBackupDto.class,
                UserAISettings.class,
                RestoreLocalBackupRequest.class,
                BulkUpdateRequest.class,
                CreateOidcProviderRequest.class,
                OidcProviderResponse.class,
                OidcProviderResponse.ProviderSource.class,
                TestOidcProviderResponse.class,
                UpdateOidcProviderRequest.class,
                UserDetailsResponse.class,
                PagedResponse.class,
                ResetPasswordResponse.class,
                UpdateSettingRequest.class,
                UpdateUserStatusRequest.class,
                UpdateUserRoleRequest.class,
                UserListResponse.class,
                SettingInfo.class,
                SettingDefinition.class,
                AuditLogResponse.class,
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
                InvitationStatus.class
        }
)
public class AdminNativeConfig {
}
