package org.github.tess1o.geopulse.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class UpdateApiTokenRequest {
    @NotBlank(message = "Token name is required")
    @Size(max = 120, message = "Token name cannot exceed 120 characters")
    @Schema(examples = "Nightly export job")
    private String name;
    @Schema(examples = "2026-12-31T23:59:59Z")
    private Instant expiresAt;
}
