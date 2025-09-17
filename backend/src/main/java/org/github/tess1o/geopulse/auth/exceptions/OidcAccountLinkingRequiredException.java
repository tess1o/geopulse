package org.github.tess1o.geopulse.auth.exceptions;

import lombok.Getter;
import java.util.List;

/**
 * Exception thrown when OIDC authentication fails due to an existing account
 * that requires linking verification through password or existing OIDC provider.
 */
@Getter
public class OidcAccountLinkingRequiredException extends RuntimeException {
    
    private final String email;
    private final String newProvider;
    private final String linkingToken;
    private final boolean hasPassword;
    private final List<String> linkedOidcProviders;
    
    public OidcAccountLinkingRequiredException(String email, 
                                             String newProvider, 
                                             String linkingToken,
                                             boolean hasPassword,
                                             List<String> linkedOidcProviders) {
        super("Account linking required for email: " + email);
        this.email = email;
        this.newProvider = newProvider;
        this.linkingToken = linkingToken;
        this.hasPassword = hasPassword;
        this.linkedOidcProviders = linkedOidcProviders;
    }
}