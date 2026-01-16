package org.github.tess1o.geopulse.auth.config;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.model.Role;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;
import org.github.tess1o.geopulse.user.model.UserEntity;
import org.github.tess1o.geopulse.user.service.UserService;

import jakarta.inject.Inject;
import java.util.Optional;

/**
 * Service for authentication configuration.
 * Uses SystemSettingsService for dynamic settings that can be changed via admin panel.
 */
@ApplicationScoped
@Slf4j
@Startup
public class AuthConfigurationService {

    @Inject
    SystemSettingsService settingsService;

    @Inject
    UserService userService;

    public boolean isPasswordRegistrationEnabled() {
        boolean registrationEnabled = settingsService.getBoolean("auth.registration.enabled");
        boolean passwordEnabled = settingsService.getBoolean("auth.password-registration.enabled");
        return registrationEnabled && passwordEnabled;
    }

    public boolean isOidcRegistrationEnabled() {
        boolean registrationEnabled = settingsService.getBoolean("auth.registration.enabled");
        boolean oidcEnabled = settingsService.getBoolean("auth.oidc.registration.enabled");
        return registrationEnabled && oidcEnabled;
    }

    public boolean isAutoLinkAccountsEnabled() {
        return settingsService.getBoolean("auth.oidc.auto-link-accounts");
    }

    public boolean isPasswordLoginEnabled() {
        boolean loginEnabled = settingsService.getBoolean("auth.login.enabled");
        boolean passwordEnabled = settingsService.getBoolean("auth.password-login.enabled");
        return loginEnabled && passwordEnabled;
    }

    public boolean isOidcLoginEnabled() {
        boolean loginEnabled = settingsService.getBoolean("auth.login.enabled");
        boolean oidcEnabled = settingsService.getBoolean("auth.oidc.login.enabled");
        return loginEnabled && oidcEnabled;
    }

    /**
     * Check if admin login bypass is enabled.
     * When false, admins are subject to the same login restrictions as regular users.
     */
    public boolean isAdminLoginBypassEnabled() {
        return settingsService.getBoolean("auth.admin-login-bypass.enabled");
    }

    /**
     * Check if password login is enabled for a specific user.
     * Admin users bypass login restrictions to prevent lockout (if bypass is enabled).
     */
    public boolean isPasswordLoginEnabledForUser(String email) {
        Optional<UserEntity> userOpt = userService.findByEmail(email);
        if (userOpt.isPresent() && userOpt.get().getRole() == Role.ADMIN && isAdminLoginBypassEnabled()) {
            log.debug("Admin user {} bypassing password login restrictions", email);
            return true; // Admin bypass
        }
        return isPasswordLoginEnabled();
    }
}
