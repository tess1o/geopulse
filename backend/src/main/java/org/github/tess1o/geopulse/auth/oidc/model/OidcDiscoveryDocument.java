package org.github.tess1o.geopulse.auth.oidc.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OidcDiscoveryDocument {
    
    private String issuer;
    
    @JsonProperty("authorization_endpoint")
    private String authorizationEndpoint;
    
    @JsonProperty("token_endpoint")
    private String tokenEndpoint;
    
    @JsonProperty("userinfo_endpoint")
    private String userinfoEndpoint;
    
    @JsonProperty("jwks_uri")
    private String jwksUri;
    
    @JsonProperty("scopes_supported")
    private String[] scopesSupported;
    
    @JsonProperty("response_types_supported")
    private String[] responseTypesSupported;
    
    @JsonProperty("subject_types_supported")
    private String[] subjectTypesSupported;
    
    @JsonProperty("id_token_signing_alg_values_supported")
    private String[] idTokenSigningAlgValuesSupported;

    /** RP-initiated logout endpoint; optional, many providers do not publish one. */
    @JsonProperty("end_session_endpoint")
    private String endSessionEndpoint;

    @JsonProperty("backchannel_logout_supported")
    private Boolean backchannelLogoutSupported;

    @JsonProperty("backchannel_logout_session_supported")
    private Boolean backchannelLogoutSessionSupported;
}