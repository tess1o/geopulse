package org.github.tess1o.geopulse.geocoding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplyNormalizationRulesResponse {
    private String jobId;
}

