package org.github.tess1o.geopulse.auth.service;

import io.quarkiverse.httpproblem.HttpProblem;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.auth.model.ApiTokenScope;

import java.util.Arrays;
import java.util.Set;

import static org.github.tess1o.geopulse.shared.api.ApiErrorCode.API_TOKEN_SCOPE_REQUIRED;

/**
 * Checks the scopes of API-token-authenticated requests. Session-authenticated and anonymous requests are never
 * restricted here; {@link ApiTokenScope#ALL} satisfies every requirement.
 */
@ApplicationScoped
public class ApiTokenScopeChecker {

    public static final String REQUIRED_SCOPE_HEADER = "X-GeoPulse-Required-Scope";

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
     * Throws a 403 problem naming the first scope of {@code anyOf} when the token has none of them.
     */
    public void requireAnyScope(SecurityIdentity identity, ApiTokenScope... anyOf) {
        if (!hasAnyScope(identity, anyOf)) {
            throw denied(anyOf[0]);
        }
    }

    public HttpProblem denied(ApiTokenScope required) {
        return HttpProblem.builder()
                .withStatus(API_TOKEN_SCOPE_REQUIRED.statusCode())
                .withTitle(API_TOKEN_SCOPE_REQUIRED.title())
                .withDetail("This API token does not have the '" + required.value() + "' scope.")
                .with("code", API_TOKEN_SCOPE_REQUIRED)
                .with("requiredScope", required.value())
                .withHeader(REQUIRED_SCOPE_HEADER, required.value())
                .build();
    }
}
