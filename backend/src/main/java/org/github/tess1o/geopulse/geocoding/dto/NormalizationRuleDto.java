package org.github.tess1o.geopulse.geocoding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.geocoding.model.NormalizationRuleType;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NormalizationRuleDto {
    private Long id;
    private NormalizationRuleType ruleType;
    private String sourceCountry;
    private String sourceCity;
    private String targetCountry;
    private String targetCity;
    private Instant createdAt;
    private Instant updatedAt;
}

