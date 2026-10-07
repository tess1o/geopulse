package org.github.tess1o.geopulse.trips.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateTripDto {

    @NotBlank(message = "Trip name cannot be empty")
    @Size(max = 150, message = "Trip name cannot exceed 150 characters")
    @Schema(examples = "Lviv weekend")
    private String name;

    @Schema(examples = "2025-06-06T00:00:00Z")
    private Instant startTime;

    @Schema(examples = "2025-06-08T23:59:59Z")
    private Instant endTime;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex color code (e.g., #FF6B6B)")
    @Schema(examples = "#3b82f6")
    private String color;

    @Size(max = 4000, message = "Notes cannot exceed 4000 characters")
    @Schema(examples = "Book the train in advance.")
    private String notes;
}
