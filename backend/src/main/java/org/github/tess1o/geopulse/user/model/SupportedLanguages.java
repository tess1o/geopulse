package org.github.tess1o.geopulse.user.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * UI languages GeoPulse ships translation catalogs for.
 *
 * <p>The stored value is the BCP 47 language subtag ({@code en}, {@code uk}) rather than an enum name,
 * matching how {@code dateFormat} and {@code timeFormat} are persisted: the frontend reads it as an
 * opaque tag and uses it directly as the vue-i18n locale and the {@code <html lang>} value. Adding a
 * language means adding a constant here plus a catalog under {@code frontend/src/locales/}.
 */
public enum SupportedLanguages {
    EN("en"),
    UK("uk");

    public static final SupportedLanguages DEFAULT = EN;

    /**
     * Regex for {@code @Pattern} and the request DTO.
     *
     * <p>Literal on purpose: annotation attributes must be compile-time constants, so this cannot be
     * derived from the constants above. {@link #assertPatternCoversAllCodes()} keeps the two in step.
     */
    public static final String PATTERN = "^(en|uk)$";

    private final String code;

    SupportedLanguages(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * Comma-separated codes, for validation error messages. Derived from the constants so a new
     * language cannot be forgotten in the message.
     */
    public static String allowedCodes() {
        return Arrays.stream(values())
                .map(SupportedLanguages::getCode)
                .collect(Collectors.joining(", "));
    }

    /**
     * Resolve a stored or submitted value, or empty when the language is not supported.
     */
    public static Optional<SupportedLanguages> fromCode(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(language -> language.code.equals(normalized))
                .findFirst();
    }

    /**
     * Normalize a value to a supported code, falling back to the default for anything unknown.
     *
     * <p>Used where an unsupported value must not be an error -- an older row, or a language that was
     * removed -- so the UI degrades to English instead of failing to render.
     */
    public static String normalizeOrDefault(String value) {
        return fromCode(value).orElse(DEFAULT).getCode();
    }

    /**
     * Fails class initialization if {@link #PATTERN} and the constants above disagree.
     *
     * <p>This is the one seam the compiler cannot check: a new constant added without widening the
     * pattern would make {@code @Pattern} reject the very language the enum accepts. Failing at class
     * load turns that into an immediate, obvious error instead of a confusing 400 in production.
     */
    private static void assertPatternCoversAllCodes() {
        for (SupportedLanguages language : values()) {
            if (!language.code.matches(PATTERN)) {
                throw new IllegalStateException(
                        "SupportedLanguages.PATTERN does not accept '" + language.code + "'; keep PATTERN in step with the constants");
            }
        }
    }

    static {
        assertPatternCoversAllCodes();
    }
}
