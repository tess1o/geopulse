package org.github.tess1o.geopulse.shared.api;

import java.lang.annotation.Annotation;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Vocabulary of Bean Validation constraints, covering the standard {@code jakarta.validation.constraints} set plus
 * GeoPulse-specific constraints. A constraint missing from this enum collapses to {@link #INVALID_VALUE}, which
 * cannot be localized — add it here when a new constraint is introduced.
 */
public enum ApiViolationCode {
    ASSERT_FALSE,
    ASSERT_TRUE,
    DECIMAL_MAX,
    DECIMAL_MIN,
    DIGITS,
    EMAIL,
    FUTURE,
    FUTURE_OR_PRESENT,
    MAX,
    MIN,
    NEGATIVE,
    NEGATIVE_OR_ZERO,
    NOT_BLANK,
    NOT_EMPTY,
    NOT_NULL,
    NULL,
    PAST,
    PAST_OR_PRESENT,
    PATTERN,
    POSITIVE,
    POSITIVE_OR_ZERO,
    SIZE,
    VALID_AREA_BOUNDS,
    INVALID_VALUE;

    private static final Pattern WORD_SEPARATOR = Pattern.compile("_([a-z])");

    /**
     * Stable translation key for this constraint, mirroring {@link ApiErrorCode#typeUri()}.
     *
     * <p>Only {@link #INVALID_VALUE} cannot be localized: it means the constraint is not part of this vocabulary,
     * so clients must fall back to {@link MessageDescriptor#fallback()}.</p>
     */
    public String messageKey() {
        String camelCase = WORD_SEPARATOR
                .matcher(name().toLowerCase(Locale.ROOT))
                .replaceAll(match -> match.group(1).toUpperCase(Locale.ROOT));
        return "validation." + camelCase;
    }

    static ApiViolationCode from(Annotation annotation) {
        try {
            return valueOf(annotation.annotationType().getSimpleName()
                    .replaceAll("([a-z])([A-Z])", "$1_$2")
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return INVALID_VALUE;
        }
    }
}
