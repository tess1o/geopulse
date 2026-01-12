package org.github.tess1o.geopulse.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthStatusResponse {
    // Registration status
    private boolean passwordRegistrationEnabled;
    private boolean oidcRegistrationEnabled;

    // Login status
    private boolean passwordLoginEnabled;
    private boolean oidcLoginEnabled;
}
