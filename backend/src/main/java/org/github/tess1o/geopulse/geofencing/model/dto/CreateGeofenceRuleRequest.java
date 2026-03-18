package org.github.tess1o.geopulse.geofencing.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateGeofenceRuleRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    @NotNull
    private UUID subjectUserId;

    @NotNull
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private Double northEastLat;

    @NotNull
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private Double northEastLon;

    @NotNull
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private Double southWestLat;

    @NotNull
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private Double southWestLon;

    @NotNull
    private Boolean monitorEnter;

    @NotNull
    private Boolean monitorLeave;

    @NotNull
    @Min(0)
    private Integer cooldownSeconds;

    private Long enterTemplateId;
    private Long leaveTemplateId;
}
