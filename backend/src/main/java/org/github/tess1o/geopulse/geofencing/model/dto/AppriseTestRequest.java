package org.github.tess1o.geopulse.geofencing.model.dto;

import lombok.Data;
import org.github.tess1o.geopulse.geofencing.model.entity.AppriseExternalRoutingMode;

@Data
public class AppriseTestRequest {
    private String destination;
    private AppriseExternalRoutingMode externalRoutingMode;
    private String appriseConfigKey;
    private String appriseTag;
    private String title;
    private String body;
}
