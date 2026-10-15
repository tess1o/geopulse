package org.github.tess1o.geopulse.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class CreateApiTokenRequest {
    @Size(max = 120, message = "Token name cannot exceed 120 characters")
    @Schema(examples = "Home dashboard")
    private String name;
    @Schema(examples = "2026-12-31T23:59:59Z")
    private Instant expiresAt;
    @NotEmpty(message = "Choose at least one scope")
    @Schema(description = "What the token may do: `all`, or any of `read`, `write`, `gps:ingest`, `export`, "
            + "`import`. `all` cannot be combined with other scopes. Scopes cannot be changed later.",
            examples = "[\"read\", \"gps:ingest\"]")
    private List<String> scopes;
}
