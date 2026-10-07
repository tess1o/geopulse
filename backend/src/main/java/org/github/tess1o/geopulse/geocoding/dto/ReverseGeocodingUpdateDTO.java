package org.github.tess1o.geopulse.geocoding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * DTO for updating reverse geocoding location fields
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReverseGeocodingUpdateDTO {

    @NotBlank(message = "Display name cannot be blank")
    @Size(max = 1000, message = "Display name must be less than 1000 characters")
    @Schema(examples = "Central Railway Station")
    private String displayName;

    @Size(max = 200, message = "City must be less than 200 characters")
    @Schema(examples = "Kyiv")
    private String city;

    @Size(max = 100, message = "Country must be less than 100 characters")
    @Schema(examples = "Ukraine")
    private String country;
}
