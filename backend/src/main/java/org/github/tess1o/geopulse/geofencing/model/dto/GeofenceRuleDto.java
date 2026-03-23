package org.github.tess1o.geopulse.geofencing.model.dto;

import lombok.Builder;
import lombok.Data;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class GeofenceRuleDto {
    private Long id;
    private String name;
    private UUID ownerUserId;
    private List<GeofenceRuleSubjectDto> subjects;
    private Double northEastLat;
    private Double northEastLon;
    private Double southWestLat;
    private Double southWestLon;
    private Boolean monitorEnter;
    private Boolean monitorLeave;
    private Integer cooldownSeconds;
    private Long enterTemplateId;
    private Long leaveTemplateId;
    private GeofenceRuleStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
