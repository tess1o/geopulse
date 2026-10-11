package org.github.tess1o.geopulse.auth.service;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

/** A browser session cookie or API session access token to validate. */
public class SessionAuthenticationRequest extends BaseAuthenticationRequest {
    private final String token;

    public SessionAuthenticationRequest(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}
