package org.github.tess1o.geopulse.trips.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateTripDto {

    @NotBlank(message = "Trip name cannot be empty")
    @Size(max = 150, message = "Trip name cannot exceed 150 characters")
    private String name;

    private Instant startTime;

    private Instant endTime;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex color code (e.g., #FF6B6B)")
    private String color;

    @Size(max = 4000, message = "Notes cannot exceed 4000 characters")
    private String notes;
}
