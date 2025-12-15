package org.github.tess1o.geopulse.favorites.model;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class EditFavoriteDto {

    @NotBlank(message = "Favorite name cannot be empty")
    @Size(max = 100, message = "Favorite name cannot exceed 100 characters")
    private String name;

    @Size(max = 200, message = "City name cannot exceed 200 characters")
    private String city;

    @Size(max = 100, message = "Country name cannot exceed 100 characters")
    private String country;
}
