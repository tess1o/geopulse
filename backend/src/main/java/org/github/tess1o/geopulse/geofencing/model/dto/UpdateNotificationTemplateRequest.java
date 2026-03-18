package org.github.tess1o.geopulse.geofencing.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateNotificationTemplateRequest {

    @Size(max = 120)
    private String name;

    private String destination;
    private String titleTemplate;
    private String bodyTemplate;
    private Boolean defaultForEnter;
    private Boolean defaultForLeave;
    private Boolean enabled;
}
