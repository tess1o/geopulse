package org.github.tess1o.geopulse.auth.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileSessionExchangeRequest {
    @NotBlank(message = "sessionCode is required")
    private String sessionCode;
}
