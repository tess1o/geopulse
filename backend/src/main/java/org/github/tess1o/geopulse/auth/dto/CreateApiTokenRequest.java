package org.github.tess1o.geopulse.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class CreateApiTokenRequest {
    @Size(max = 120, message = "Token name cannot exceed 120 characters")
    private String name;
    private Instant expiresAt;
}
