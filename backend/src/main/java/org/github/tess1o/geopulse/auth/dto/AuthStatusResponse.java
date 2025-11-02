package org.github.tess1o.geopulse.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthStatusResponse {
    private boolean passwordRegistrationEnabled;
    private boolean oidcRegistrationEnabled;
}
