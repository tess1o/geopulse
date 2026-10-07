package org.github.tess1o.geopulse.shared.openapi;

/**
 * OpenAPI vendor extensions used by GeoPulse.
 */
public final class ApiExtensions {

    /**
     * Marks an operation that only exists for the GeoPulse web app. It stays in the OpenAPI document, but the
     * documentation site leaves it out and it may change without notice. Use as
     * {@code @Extension(name = ApiExtensions.INTERNAL, value = "true", parseValue = true)}.
     */
    public static final String INTERNAL = "x-internal";

    private ApiExtensions() {
    }
}
