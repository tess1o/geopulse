package org.github.tess1o.geopulse.favorites.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.github.tess1o.geopulse.favorites.model.AddAreaToFavoritesDto;

public class ValidAreaBoundsValidator implements ConstraintValidator<ValidAreaBounds, AddAreaToFavoritesDto> {

    @Override
    public void initialize(ValidAreaBounds constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(AddAreaToFavoritesDto dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true; // Let @NotNull handle null validation
        }

        try {
            org.github.tess1o.geopulse.favorites.model.AreaGeometry.resolve(dto.getGeometry(),
                    dto.getNorthEastLat(), dto.getNorthEastLon(), dto.getSouthWestLat(), dto.getSouthWestLon());
            return true;
        } catch (IllegalArgumentException e) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(e.getMessage()).addConstraintViolation();
            return false;
        }
    }
}
