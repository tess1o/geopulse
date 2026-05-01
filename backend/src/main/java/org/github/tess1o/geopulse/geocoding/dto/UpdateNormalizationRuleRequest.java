package org.github.tess1o.geopulse.geocoding.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.geocoding.model.NormalizationRuleType;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateNormalizationRuleRequest {

    @NotNull(message = "Rule type is required")
    private NormalizationRuleType ruleType;

    @Size(max = 100, message = "Source country must be less than 100 characters")
    private String sourceCountry;

    @Size(max = 200, message = "Source city must be less than 200 characters")
    private String sourceCity;

    @Size(max = 100, message = "Target country must be less than 100 characters")
    private String targetCountry;

    @Size(max = 200, message = "Target city must be less than 200 characters")
    private String targetCity;

    @AssertTrue(message = "Country rule requires sourceCountry and targetCountry")
    public boolean isCountryRuleValid() {
        if (ruleType != NormalizationRuleType.COUNTRY) {
            return true;
        }
        return hasText(sourceCountry) && hasText(targetCountry);
    }

    @AssertTrue(message = "City rule requires sourceCity and targetCity")
    public boolean isCityRuleValid() {
        if (ruleType != NormalizationRuleType.CITY) {
            return true;
        }
        return hasText(sourceCity) && hasText(targetCity);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
