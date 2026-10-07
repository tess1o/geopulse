package org.github.tess1o.geopulse.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {
    @Schema(examples = "user@example.com")
    private String email;
    @Schema(examples = "correct-horse-battery")
    private String password;
}
