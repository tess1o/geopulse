package org.github.tess1o.geopulse.user.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * The value must be the exact code of a {@link org.github.tess1o.geopulse.user.model.SupportedLanguages}
 * constant. {@code null} is valid so partial updates can omit the field.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SupportedLanguageValidator.class)
@Documented
public @interface SupportedLanguage {
    String message() default "Unsupported language";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
