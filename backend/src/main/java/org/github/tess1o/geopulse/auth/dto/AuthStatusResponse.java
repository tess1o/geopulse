package org.github.tess1o.geopulse.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthStatusResponse {
    // Registration status
    private boolean passwordRegistrationEnabled;
    private boolean oidcRegistrationEnabled;

    // Login status
    private boolean passwordLoginEnabled;
    private boolean oidcLoginEnabled;
    private boolean adminLoginBypassEnabled;
    // Send the login page straight to the only OIDC provider (the frontend checks the other conditions)
    private boolean oidcAutoLaunchEnabled;

    // Guest root navigation
    private boolean guestRootRedirectToLoginEnabled;

    // Demo mode
    private boolean demoModeEnabled;
    private boolean demoAdminReadOnlyEnabled;
    private List<DemoPersonaResponse> demoPersonas;
}
