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
 * language is done by {@code npm run i18n:add} in {@code frontend/}, which adds the constant here and the
 * catalog under {@code frontend/src/locales/} (see the README there).
 */
public enum SupportedLanguages {
    EN("en"),
    UK("uk"),
    // add-locale:entries -- `npm run i18n:add` inserts new constants above this line.
    ;

    public static final SupportedLanguages DEFAULT = EN;

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
     * Whether a submitted value is exactly a supported code. Case-sensitive on purpose: the stored value
     * is used verbatim as a vue-i18n locale and an {@code <html lang>}, so {@code "UK"} must be rejected
     * rather than normalized into something the client cannot map. Backs {@code @SupportedLanguage}.
     */
    public static boolean isExactCode(String value) {
        return value != null && Arrays.stream(values()).anyMatch(language -> language.code.equals(value));
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
}
