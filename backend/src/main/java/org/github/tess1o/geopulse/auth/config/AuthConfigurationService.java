package org.github.tess1o.geopulse.auth.config;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.github.tess1o.geopulse.admin.service.SystemSettingsService;

import jakarta.inject.Inject;

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
}
