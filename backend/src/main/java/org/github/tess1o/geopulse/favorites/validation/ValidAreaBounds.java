package org.github.tess1o.geopulse.favorites.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidAreaBoundsValidator.class)
@Documented
public @interface ValidAreaBounds {
    String message() default "North-East coordinates must be greater than South-West coordinates";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}