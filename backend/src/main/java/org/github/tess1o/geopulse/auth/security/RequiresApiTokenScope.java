package org.github.tess1o.geopulse.auth.security;

import org.github.tess1o.geopulse.auth.model.ApiTokenScope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the API token scope a resource method requires: the token needs any one of {@link #value()}. Without
 * it, safe HTTP methods require {@link ApiTokenScope#READ} and all others {@link ApiTokenScope#WRITE}; use it for
 * POST endpoints that only read data (previews, searches). Has no effect on session-authenticated requests.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresApiTokenScope {
    ApiTokenScope[] value();
}
