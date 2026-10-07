package org.github.tess1o.geopulse.geofencing.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class CreateGeofenceRuleRequest {

    @NotBlank
    @Size(max = 120)
    @Schema(examples = "Office")
    private String name;

    @NotEmpty
    private List<@NotNull UUID> subjectUserIds;

    @NotNull
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    @Schema(examples = "50.452")
    private Double northEastLat;

    @NotNull
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    @Schema(examples = "30.526")
    private Double northEastLon;

    @NotNull
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    @Schema(examples = "50.449")
    private Double southWestLat;

    @NotNull
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    @Schema(examples = "30.521")
    private Double southWestLon;

    @NotNull
    @Schema(examples = "true")
    private Boolean monitorEnter;

    @NotNull
    @Schema(examples = "true")
    private Boolean monitorLeave;

    @NotNull
    @Min(0)
    @Schema(examples = "600")
    private Integer cooldownSeconds;

    @Schema(examples = "3")
    private Long enterTemplateId;
    @Schema(examples = "4")
    private Long leaveTemplateId;
}
