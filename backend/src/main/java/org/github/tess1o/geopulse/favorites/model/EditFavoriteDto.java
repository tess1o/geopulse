package org.github.tess1o.geopulse.favorites.model;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class EditFavoriteDto {

    @NotBlank(message = "Favorite name cannot be empty")
    @Size(max = 100, message = "Favorite name cannot exceed 100 characters")
    @Schema(examples = "Home")
    private String name;

    @Size(max = 200, message = "City name cannot exceed 200 characters")
    @Schema(examples = "Kyiv")
    private String city;

    @Size(max = 100, message = "Country name cannot exceed 100 characters")
    @Schema(examples = "Ukraine")
    private String country;

    // Optional bounds for area favorites
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    @Schema(examples = "50.452")
    private Double northEastLat;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    @Schema(examples = "30.526")
    private Double northEastLon;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    @Schema(examples = "50.449")
    private Double southWestLat;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    @Schema(examples = "30.521")
    private Double southWestLon;
}
