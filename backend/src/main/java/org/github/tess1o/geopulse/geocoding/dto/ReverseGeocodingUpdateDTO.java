package org.github.tess1o.geopulse.geocoding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String displayName;

    @Size(max = 200, message = "City must be less than 200 characters")
    private String city;

    @Size(max = 100, message = "Country must be less than 100 characters")
    private String country;
}
