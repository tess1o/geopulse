package org.github.tess1o.geopulse.shared.api;

import java.lang.annotation.Annotation;

public enum ApiViolationCode {
    ASSERT_TRUE,
    DECIMAL_MAX,
    DECIMAL_MIN,
    EMAIL,
    FUTURE,
    MAX,
    MIN,
    NOT_BLANK,
    NOT_EMPTY,
    NOT_NULL,
    PATTERN,
    POSITIVE,
    SIZE,
    VALID_AREA_BOUNDS,
    INVALID_VALUE;

    static ApiViolationCode from(Annotation annotation) {
        try {
            return valueOf(annotation.annotationType().getSimpleName()
                    .replaceAll("([a-z])([A-Z])", "$1_$2")
                    .toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return INVALID_VALUE;
        }
    }
}
