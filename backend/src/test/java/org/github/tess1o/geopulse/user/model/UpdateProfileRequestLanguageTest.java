package org.github.tess1o.geopulse.user.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UpdateProfileRequestLanguageTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static UpdateProfileRequest withLanguage(String language) {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUiPreferences(UserUiPreferences.builder().language(language).build());
        return request;
    }

    private boolean hasLanguageViolation(UpdateProfileRequest request) {
        return validator.validate(request).stream()
                .anyMatch(violation -> "uiPreferences.language".equals(violation.getPropertyPath().toString()));
    }

    @Test
    void acceptsEachSupportedLanguage() {
        for (SupportedLanguages language : SupportedLanguages.values()) {
            UpdateProfileRequest request = withLanguage(language.getCode());
            assertTrue(validator.validate(request).isEmpty(),
                    "Expected '" + language.getCode() + "' to be accepted");
        }
    }

    @Test
    void rejectsUnsupportedLanguages() {
        UpdateProfileRequest request = withLanguage("de");
        assertTrue(hasLanguageViolation(request));
    }

    @Test
    void rejectsACodeDifferingOnlyByCase() {
        // The stored value is used verbatim as a vue-i18n locale and an <html lang>, so a stray "UK"
        // must be rejected by the boundary rather than normalized into something the client cannot map.
        UpdateProfileRequest request = withLanguage("UK");
        assertTrue(hasLanguageViolation(request));
    }

    @Test
    void rejectsBlankAndOversizedValues() {
        UpdateProfileRequest blank = withLanguage("");
        assertTrue(hasLanguageViolation(blank));

        UpdateProfileRequest oversized = withLanguage("x".repeat(17));
        assertTrue(hasLanguageViolation(oversized));
    }

    @Test
    void allowsOmittingLanguageSoPartialUpdatesKeepTheStoredValue() {
        // updateProfile only applies fields that are non-null, so a PATCH omitting `language` must
        // validate cleanly rather than clearing the user's choice.
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Some Name");
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void defaultsAnEntityToEnglish() {
        assertEquals("en", UserEntity.builder().build().getUiPreferences().withDefaults().getLanguage());
    }
}
