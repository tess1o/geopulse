package org.github.tess1o.geopulse.admin;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.github.tess1o.geopulse.admin.dto.*;
import org.github.tess1o.geopulse.admin.model.*;

@RegisterForReflection(
        targets = {
                // DTOs
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
                // Entities
                OidcProviderEntity.class,
                SystemSettingsEntity.class,
                AuditLogEntity.class,
                // Enums
                Role.class,
                ActionType.class,
                TargetType.class,
                ValueType.class,
                SettingsCategory.class
        }
)
public class AdminNativeConfig {
}
