package org.github.tess1o.geopulse.favorites.model;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BulkUpdateFavoritesDto {

    @NotNull(message = "Favorite IDs cannot be null")
    @NotEmpty(message = "Favorite IDs cannot be empty")
    private List<Long> favoriteIds;

    private Boolean updateCity;

    @Size(max = 200, message = "City name cannot exceed 200 characters")
    private String city;

    private Boolean updateCountry;

    @Size(max = 100, message = "Country name cannot exceed 100 characters")
    private String country;

    /**
     * Validates that if a field is marked for update, it must have a non-blank value.
     */
    @AssertTrue(message = "City value is required when updateCity is true")
    public boolean isCityValid() {
        if (Boolean.TRUE.equals(updateCity)) {
            return city != null && !city.trim().isEmpty();
        }
        return true;
    }

    @AssertTrue(message = "Country value is required when updateCountry is true")
    public boolean isCountryValid() {
        if (Boolean.TRUE.equals(updateCountry)) {
            return country != null && !country.trim().isEmpty();
        }
        return true;
    }

    @AssertTrue(message = "At least one field must be selected for update")
    public boolean isAtLeastOneFieldSelected() {
        return Boolean.TRUE.equals(updateCity) || Boolean.TRUE.equals(updateCountry);
    }
}
