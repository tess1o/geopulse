package org.github.tess1o.geopulse.geofencing.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateNotificationTemplateRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    private String destination;

    private String titleTemplate;
    private String bodyTemplate;

    @NotNull
    private Boolean defaultForEnter;

    @NotNull
    private Boolean defaultForLeave;

    @NotNull
    private Boolean enabled;
}
