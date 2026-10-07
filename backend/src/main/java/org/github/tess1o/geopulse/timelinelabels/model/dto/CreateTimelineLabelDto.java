package org.github.tess1o.geopulse.timelinelabels.model.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateTimelineLabelDto {

    @NotBlank(message = "Label name cannot be empty")
    @Size(max = 100, message = "Label name cannot exceed 100 characters")
    @Schema(examples = "Summer vacation")
    private String name;

    @NotNull(message = "Start time is required")
    @Schema(examples = "2025-07-01T00:00:00Z")
    private Instant startTime;

    @Schema(examples = "2025-07-14T23:59:59Z")
    private Instant endTime;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex color code (e.g., #FF6B6B)")
    @Schema(examples = "#f59e0b")
    private String color;

    @Schema(examples = "true")
    private Boolean showAsPreset;
}
