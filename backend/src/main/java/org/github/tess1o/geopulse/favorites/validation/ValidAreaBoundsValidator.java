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

        boolean isValid = true;
        
        // Validate latitude bounds
        if (dto.getNorthEastLat() <= dto.getSouthWestLat()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "North-East latitude (" + dto.getNorthEastLat() + 
                    ") must be greater than South-West latitude (" + dto.getSouthWestLat() + ")")
                    .addPropertyNode("northEastLat")
                    .addConstraintViolation();
            isValid = false;
        }
        
        // Validate longitude bounds
        if (dto.getNorthEastLon() <= dto.getSouthWestLon()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "North-East longitude (" + dto.getNorthEastLon() + 
                    ") must be greater than South-West longitude (" + dto.getSouthWestLon() + ")")
                    .addPropertyNode("northEastLon")
                    .addConstraintViolation();
            isValid = false;
        }
        
        return isValid;
    }
}