package org.github.tess1o.geopulse.auth.oidc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OidcUserInfo {
    @JsonProperty("sub")
    private String subject;        // Unique identifier from OIDC provider
    
    private String email;
    
    @JsonProperty("email_verified")
    private Boolean emailVerified;
    
    private String name;
    
    @JsonProperty("given_name")
    private String givenName;
    
    @JsonProperty("family_name")
    private String familyName;
    
    private String picture;        // Avatar URL
    
    private String locale;
}