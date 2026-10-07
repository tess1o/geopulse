package org.github.tess1o.geopulse.geocoding.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplyNormalizationRulesRequest {

    @Schema(examples = "true")
    private Boolean applyToGeocoding;
    @Schema(examples = "true")
    private Boolean applyToFavorites;

    @AssertTrue(message = "At least one scope must be selected")
    public boolean isScopeValid() {
        return Boolean.TRUE.equals(applyToGeocoding) || Boolean.TRUE.equals(applyToFavorites);
    }
}

