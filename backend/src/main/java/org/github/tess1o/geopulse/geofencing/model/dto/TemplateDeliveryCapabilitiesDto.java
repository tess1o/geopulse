package org.github.tess1o.geopulse.geofencing.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateDeliveryCapabilitiesDto {
    private boolean appriseEnabled;
    private boolean appriseConfigured;
}
