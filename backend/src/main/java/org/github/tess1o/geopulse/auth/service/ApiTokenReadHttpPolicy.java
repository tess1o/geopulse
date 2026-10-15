package org.github.tess1o.geopulse.auth.service;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.github.tess1o.geopulse.auth.model.ApiTokenScope;

/**
 * HTTP permission policy for non-JAX-RS endpoints (MCP) whose tools only read data: requires an authenticated
 * user and, for API tokens, the {@code read} scope. Configured via
 * {@code quarkus.http.auth.permission.mcp.policy=api-token-read}.
 */
@ApplicationScoped
public class ApiTokenReadHttpPolicy implements HttpSecurityPolicy {

    public static final String NAME = "api-token-read";

    @Inject
    ApiTokenScopeChecker scopeChecker;

    @Override
    public Uni<CheckResult> checkPermission(RoutingContext request, Uni<SecurityIdentity> identity,
                                            AuthorizationRequestContext requestContext) {
        return identity.map(current -> {
            if (current == null || current.isAnonymous()) {
                return CheckResult.DENY;
            }
            return scopeChecker.hasAnyScope(current, ApiTokenScope.READ) ? CheckResult.PERMIT : CheckResult.DENY;
        });
    }

    @Override
    public String name() {
        return NAME;
    }
}
