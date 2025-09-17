package org.github.tess1o.geopulse.auth.oidc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for account linking requirement scenarios.
 * Provides information needed for the frontend to display appropriate linking options.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OidcAccountLinkingErrorResponse {
    
    private String error;
    private String email;
    private String newProvider;
    private String linkingToken;
    private String message;
    
    /**
     * Information about available verification methods for account linking
     */
    private VerificationMethods verificationMethods;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificationMethods {
        private boolean password;
        private List<String> oidcProviders;
    }
}