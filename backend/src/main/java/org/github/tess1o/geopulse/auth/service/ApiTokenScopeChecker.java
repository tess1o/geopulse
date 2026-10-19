package org.github.tess1o.geopulse.auth.service;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.auth.model.ApiTokenScope;
import org.github.tess1o.geopulse.shared.api.GeoPulseException;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.API_TOKEN_SCOPE_REQUIRED;

/**
 * Checks the scopes of API-token-authenticated requests. Session-authenticated and anonymous requests are never
 * restricted here; {@link ApiTokenScope#ALL} satisfies every requirement.
 */
@ApplicationScoped
public class ApiTokenScopeChecker {

    public boolean isApiToken(SecurityIdentity identity) {
        return identity != null && !identity.isAnonymous()
                && IdentityAttributes.AUTH_TYPE_API_TOKEN.equals(identity.getAttribute(IdentityAttributes.AUTH_TYPE));
    }

    /** An API token without full access: responses must not include stored credentials of other integrations. */
    public boolean isLimitedApiToken(SecurityIdentity identity) {
        return isApiToken(identity) && !hasAnyScope(identity, ApiTokenScope.ALL);
    }

    /** True when the request is not API-token-authenticated, or the token has any of the given scopes. */
    public boolean hasAnyScope(SecurityIdentity identity, ApiTokenScope... anyOf) {
        if (!isApiToken(identity)) {
            return true;
        }
        Set<ApiTokenScope> scopes = identity.getAttribute(IdentityAttributes.API_TOKEN_SCOPES);
        if (scopes == null) {
            return false;
        }
        return scopes.contains(ApiTokenScope.ALL) || Arrays.stream(anyOf).anyMatch(scopes::contains);
    }

    /**
     * Throws a 403 problem naming the first scope of {@code anyOf} when the token has none of them. The problem
     * envelope also exposes the scope as {@code requiredScope} and in a header; see {@code ProblemFactory}.
     */
    public void requireAnyScope(SecurityIdentity identity, ApiTokenScope... anyOf) {
        if (!hasAnyScope(identity, anyOf)) {
            ApiTokenScope required = anyOf[0];
            throw new GeoPulseException(API_TOKEN_SCOPE_REQUIRED,
                    "This API token does not have the '" + required.value() + "' scope.",
                    Map.of(GeoPulseException.REQUIRED_SCOPE, required.value()));
        }
    }
}
