package org.github.tess1o.geopulse.geofencing.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class NotificationTemplateDto {
    private Long id;
    private String name;
    private String destination;
    private String titleTemplate;
    private String bodyTemplate;
    private Boolean defaultForEnter;
    private Boolean defaultForLeave;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
