package org.github.tess1o.geopulse.geofencing.model.dto;

import lombok.Builder;
import lombok.Data;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceDeliveryStatus;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceEventType;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class GeofenceEventDto {
    private Long id;
    private Long ruleId;
    private String ruleName;
    private UUID subjectUserId;
    private String subjectDisplayName;
    private GeofenceEventType eventType;
    private Instant occurredAt;
    private String title;
    private String message;
    private GeofenceDeliveryStatus deliveryStatus;
    private Integer deliveryAttempts;
    private String lastDeliveryError;
    private Instant deliveredAt;
    private Long pointId;
    private Double pointLat;
    private Double pointLon;
    private Instant seenAt;
    private Boolean seen;
}
