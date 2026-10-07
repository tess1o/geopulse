package org.github.tess1o.geopulse.auth.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileSessionExchangeRequest {
    @NotBlank(message = "sessionCode is required")
    @Schema(examples = "k3J9xQ2mP7")
    private String sessionCode;
}
