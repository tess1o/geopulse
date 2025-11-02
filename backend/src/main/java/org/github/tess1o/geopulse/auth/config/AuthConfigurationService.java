package org.github.tess1o.geopulse.auth.config;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.inject.Inject;

@ApplicationScoped
@Slf4j
@Startup
public class AuthConfigurationService {

    @Inject
    Config config;

    @ConfigProperty(name = "geopulse.auth.registration.enabled", defaultValue = "true")
    boolean registrationEnabled;

    @ConfigProperty(name = "geopulse.auth.password-registration.enabled", defaultValue = "true")
    boolean passwordRegistrationEnabled;

    @ConfigProperty(name = "geopulse.auth.oidc.registration.enabled", defaultValue = "true")
    boolean oidcRegistrationEnabled;

    private static final String DEPRECATED_PROPERTY = "geoupuse.auth.sign-up-enabled";

    public boolean isPasswordRegistrationEnabled() {
        boolean specificPasswordEnabled = passwordRegistrationEnabled;

        // Check for deprecated property if new one is not explicitly set
        if (config.getOptionalValue("geopulse.auth.password-registration.enabled", String.class).isEmpty() &&
            config.getOptionalValue(DEPRECATED_PROPERTY, String.class).isPresent()) {
            specificPasswordEnabled = config.getValue(DEPRECATED_PROPERTY, Boolean.class);
            log.warn("The configuration property '{}' is deprecated and will be removed in a future release. Please use '{}' instead.",
                    DEPRECATED_PROPERTY, "geopulse.auth.password-registration.enabled");
        }

        return registrationEnabled && specificPasswordEnabled;
    }

    public boolean isOidcRegistrationEnabled() {
        return registrationEnabled && oidcRegistrationEnabled;
    }
}
