package org.github.tess1o.geopulse.timelinelabels.model.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTimelineLabelDto {

    @NotBlank(message = "Label name cannot be empty")
    @Size(max = 100, message = "Label name cannot exceed 100 characters")
    private String name;

    @NotNull(message = "Start time is required")
    private Instant startTime;

    private Instant endTime;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex color code (e.g., #FF6B6B)")
    private String color;

    private Boolean showAsPreset;
}
