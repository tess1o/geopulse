package org.github.tess1o.geopulse.favorites.model;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddPointToFavoritesDto {
    
    @NotBlank(message = "Favorite name cannot be empty")
    @Size(max = 100, message = "Favorite name cannot exceed 100 characters")
    @Schema(examples = "Home")
    private String name;
    
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    @Schema(examples = "50.4501")
    private double lat;
    
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    @Schema(examples = "30.5234")
    private double lon;
}
