package org.github.tess1o.geopulse.periods.model.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreatePeriodTagDto {

    @NotBlank(message = "Tag name cannot be empty")
    @Size(max = 100, message = "Tag name cannot exceed 100 characters")
    private String tagName;

    @NotNull(message = "Start time is required")
    private Instant startTime;

    private Instant endTime;

    private String source;

    private Boolean isActive;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex color code (e.g., #FF6B6B)")
    private String color;
}
