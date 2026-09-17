package org.github.tess1o.geopulse.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.api.ApiErrorCode;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestOidcProviderResponse {

    private boolean success;
    private ApiErrorCode code;
    private String detail;

    // Discovered endpoints (if successful)
    private String authorizationEndpoint;
    private String tokenEndpoint;
    private String userinfoEndpoint;
    private String jwksUri;
    private String issuer;

    // Error information (if failed)
    private String errorType;
    private String errorDetails;

    public static TestOidcProviderResponse success(
            String authorizationEndpoint,
            String tokenEndpoint,
            String userinfoEndpoint,
            String jwksUri,
            String issuer) {
        return TestOidcProviderResponse.builder()
                .success(true)
                .authorizationEndpoint(authorizationEndpoint)
                .tokenEndpoint(tokenEndpoint)
                .userinfoEndpoint(userinfoEndpoint)
                .jwksUri(jwksUri)
                .issuer(issuer)
                .build();
    }

    public static TestOidcProviderResponse failure(String errorType, String errorDetails) {
        return TestOidcProviderResponse.builder()
                .success(false)
                .code(ApiErrorCode.OIDC_PROVIDER_CONNECTION_FAILED)
                .detail("Failed to connect to OIDC provider")
                .errorType(errorType)
                .errorDetails(errorDetails)
                .build();
    }
}
