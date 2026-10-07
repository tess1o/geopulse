package org.github.tess1o.geopulse.favorites.model;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.favorites.validation.ValidAreaBounds;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ValidAreaBounds
public class AddAreaToFavoritesDto {
    
    @NotBlank(message = "Favorite name cannot be empty")
    @Size(max = 100, message = "Favorite name cannot exceed 100 characters")
    @Schema(examples = "Office campus")
    private String name;
    
    @DecimalMin(value = "-90.0", message = "North-East latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "North-East latitude must be between -90 and 90")
    @Schema(examples = "50.452")
    private double northEastLat;
    
    @DecimalMin(value = "-180.0", message = "North-East longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "North-East longitude must be between -180 and 180")
    @Schema(examples = "30.526")
    private double northEastLon;
    
    @DecimalMin(value = "-90.0", message = "South-West latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "South-West latitude must be between -90 and 90")
    @Schema(examples = "50.449")
    private double southWestLat;
    
    @DecimalMin(value = "-180.0", message = "South-West longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "South-West longitude must be between -180 and 180")
    @Schema(examples = "30.521")
    private double southWestLon;
}
