package org.github.tess1o.geopulse.geofencing.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import org.github.tess1o.geopulse.geofencing.model.entity.AppriseExternalRoutingMode;

@Data
public class UpdateNotificationTemplateRequest {

    @Size(max = 120)
    private String name;

    private String destination;
    private AppriseExternalRoutingMode externalRoutingMode;
    private String appriseConfigKey;
    private String appriseTag;
    private String titleTemplate;
    private String bodyTemplate;
    private Boolean defaultForEnter;
    private Boolean defaultForLeave;
    private Boolean enabled;
    private Boolean sendInApp;
}
