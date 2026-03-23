package org.github.tess1o.geopulse.geofencing.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleStatus;

import java.util.List;
import java.util.UUID;

@Data
public class UpdateGeofenceRuleRequest {

    @Size(max = 120)
    private String name;

    private List<@NotNull UUID> subjectUserIds;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private Double northEastLat;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private Double northEastLon;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private Double southWestLat;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private Double southWestLon;

    private Boolean monitorEnter;
    private Boolean monitorLeave;

    @Min(0)
    private Integer cooldownSeconds;

    private Long enterTemplateId;
    private Long leaveTemplateId;

    private GeofenceRuleStatus status;
}
