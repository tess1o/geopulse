package org.github.tess1o.geopulse.geofencing.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.github.tess1o.geopulse.geofencing.model.entity.GeofenceRuleStatus;

import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class UpdateGeofenceRuleRequest {

    @Size(max = 120)
    @Schema(examples = "Office")
    private String name;

    private List<@NotNull UUID> subjectUserIds;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    @Schema(examples = "50.452")
    private Double northEastLat;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    @Schema(examples = "30.526")
    private Double northEastLon;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    @Schema(examples = "50.449")
    private Double southWestLat;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    @Schema(examples = "30.521")
    private Double southWestLon;

    @Schema(examples = "true")
    private Boolean monitorEnter;
    @Schema(examples = "true")
    private Boolean monitorLeave;

    @Min(0)
    @Schema(examples = "600")
    private Integer cooldownSeconds;

    @Schema(examples = "3")
    private Long enterTemplateId;
    @Schema(examples = "4")
    private Long leaveTemplateId;

    @Schema(examples = "ACTIVE")
    private GeofenceRuleStatus status;
}
