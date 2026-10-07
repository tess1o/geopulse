package org.github.tess1o.geopulse.gps.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * DTO for updating GPS point data.
 * Allows editing of location, speed, and accuracy.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditGpsPointDto {
    
    @NotNull(message = "Coordinates are required")
    @Valid
    private CoordinatesDto coordinates;
    
    @PositiveOrZero(message = "Velocity must be zero or positive")
    @Schema(examples = "4.8")
    private Double velocity;
    
    @PositiveOrZero(message = "Accuracy must be zero or positive")
    @Schema(examples = "12.0")
    private Double accuracy;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoordinatesDto {
        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        @Schema(examples = "50.4501")
        private Double lat;
        
        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        @Schema(examples = "30.5234")
        private Double lng;
    }
}