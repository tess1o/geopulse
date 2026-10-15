package org.github.tess1o.geopulse.auth.service;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import org.github.tess1o.geopulse.auth.model.ApiTokenScope;
import org.github.tess1o.geopulse.auth.security.RequiresApiTokenScope;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * Enforces API token scopes on REST endpoints. Rules, first match wins:
 * <ol>
 *     <li>Account security and admin paths need {@link ApiTokenScope#ALL}.</li>
 *     <li>A {@link RequiresApiTokenScope} annotation on the method or class.</li>
 *     <li>Ingest, export and import paths need their own scope (reads also accept {@code read}).</li>
 *     <li>Safe methods need {@code read}; everything else needs {@code write}.</li>
 * </ol>
 * The MCP endpoint is not JAX-RS and is covered by {@link ApiTokenReadHttpPolicy}.
 */
@Provider
@Priority(Priorities.AUTHORIZATION + 10)
public class ApiTokenScopeFilter implements ContainerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /** Paths a limited token can never use, not even for reading. */
    private static final List<String> FULL_ACCESS_PREFIXES = List.of(
            "api/v1/api-tokens",
            "api/v1/users/me/password",
            "api/v1/auth",
            "api/v1/admin",
            "api/v1/debug-imports");

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    ApiTokenScopeChecker scopeChecker;

    @Inject
    ApiTokenSecretService apiTokenSecretService;

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Only touch the (lazily authenticated) identity when an API token was actually sent
        if (!hasApiTokenCredentials(requestContext) || !scopeChecker.isApiToken(securityIdentity)) {
            return;
        }

        String method = requestContext.getMethod().toUpperCase(Locale.ROOT);
        String path = normalizePath(requestContext.getUriInfo().getRequestUri().getPath());
        ApiTokenScope[] required = requiredScopes(method, path);
        if (required.length > 0) {
            scopeChecker.requireAnyScope(securityIdentity, required);
        }
    }

    ApiTokenScope[] requiredScopes(String method, String path) {
        boolean safe = isSafeMethod(method);
        if ("GET".equals(method) && path.equals("api/v1/api-tokens/current")) {
            return new ApiTokenScope[0];
        }
        if (FULL_ACCESS_PREFIXES.stream().anyMatch(prefix -> matchesPrefix(path, prefix))) {
            return new ApiTokenScope[]{ApiTokenScope.ALL};
        }

        RequiresApiTokenScope annotation = findAnnotation();
        if (annotation != null) {
            return annotation.value();
        }

        if (matchesPrefix(path, "api/v1/gps/ingest") || path.startsWith("api/homeassistant")) {
            return new ApiTokenScope[]{ApiTokenScope.GPS_INGEST};
        }
        if ("POST".equals(method) && path.equals("api/v1/gps/points")) {
            return new ApiTokenScope[]{ApiTokenScope.GPS_INGEST, ApiTokenScope.WRITE};
        }
        if (matchesPrefix(path, "api/v1/exports")) {
            return safe
                    ? new ApiTokenScope[]{ApiTokenScope.EXPORT, ApiTokenScope.READ}
                    : new ApiTokenScope[]{ApiTokenScope.EXPORT};
        }
        if (matchesPrefix(path, "api/v1/imports") || matchesPrefix(path, "api/v1/import-uploads")) {
            // Imports that replace existing data additionally need `write` (checked in the import resources)
            return safe
                    ? new ApiTokenScope[]{ApiTokenScope.IMPORT, ApiTokenScope.READ}
                    : new ApiTokenScope[]{ApiTokenScope.IMPORT};
        }
        return safe ? new ApiTokenScope[]{ApiTokenScope.READ} : new ApiTokenScope[]{ApiTokenScope.WRITE};
    }

    private RequiresApiTokenScope findAnnotation() {
        if (resourceInfo == null) {
            return null;
        }
        Method method = resourceInfo.getResourceMethod();
        if (method != null && method.isAnnotationPresent(RequiresApiTokenScope.class)) {
            return method.getAnnotation(RequiresApiTokenScope.class);
        }
        Class<?> resourceClass = resourceInfo.getResourceClass();
        return resourceClass != null ? resourceClass.getAnnotation(RequiresApiTokenScope.class) : null;
    }

    private boolean hasApiTokenCredentials(ContainerRequestContext requestContext) {
        String apiKey = requestContext.getHeaderString(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return true;
        }
        String authorization = requestContext.getHeaderString(AUTHORIZATION_HEADER);
        return authorization != null && authorization.startsWith(BEARER_PREFIX)
                && apiTokenSecretService.hasTokenPrefix(authorization.substring(BEARER_PREFIX.length()).trim());
    }

    private static boolean isSafeMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }

    private static boolean matchesPrefix(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
