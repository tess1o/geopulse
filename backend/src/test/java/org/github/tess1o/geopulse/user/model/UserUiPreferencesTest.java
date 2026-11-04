package org.github.tess1o.geopulse.user.model;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UserUiPreferencesTest {
    @Test
    void acceptsSupportedDateFormatsAndRejectsUnknownFormatsInProfileUpdates() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String format : new String[]{"MDY", "DMY", "DMY_DOT", "YMD"}) {
                var request = new UpdateProfileRequest();
                request.setUiPreferences(UserUiPreferences.builder().dateFormat(format).build());
                assertTrue(validator.validate(request).isEmpty(), format);
            }
            var request = new UpdateProfileRequest();
            request.setUiPreferences(UserUiPreferences.builder().dateFormat("UNKNOWN").build());
            assertFalse(validator.validate(request).isEmpty());
        }
    }
}
