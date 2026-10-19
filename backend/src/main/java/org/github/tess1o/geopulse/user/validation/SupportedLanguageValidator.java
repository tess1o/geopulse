package org.github.tess1o.geopulse.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.github.tess1o.geopulse.user.model.SupportedLanguages;

/**
 * Checks against the enum itself rather than a regex literal, so adding a language is one new constant.
 */
public class SupportedLanguageValidator implements ConstraintValidator<SupportedLanguage, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || SupportedLanguages.isExactCode(value)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("Language must be one of: " + SupportedLanguages.allowedCodes())
                .addConstraintViolation();
        return false;
    }
}
