package org.github.tess1o.geopulse.geofencing.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.github.tess1o.geopulse.geofencing.model.entity.AppriseExternalRoutingMode;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class CreateNotificationTemplateRequest {

    @NotBlank
    @Size(max = 120)
    @Schema(examples = "Telegram alert")
    private String name;

    @Schema(examples = "tgram://bottoken/ChatID")
    private String destination;
    @Schema(examples = "URLS")
    private AppriseExternalRoutingMode externalRoutingMode;
    @Schema(examples = "geopulse")
    private String appriseConfigKey;
    @Schema(examples = "family")
    private String appriseTag;

    @Schema(examples = "Geofence Alert: {{geofenceName}}")
    private String titleTemplate;
    @Schema(examples = "{{subjectName}} {{eventVerb}} {{geofenceName}} at {{timestamp}}")
    private String bodyTemplate;

    @NotNull
    @Schema(examples = "true")
    private Boolean defaultForEnter;

    @NotNull
    @Schema(examples = "false")
    private Boolean defaultForLeave;

    @NotNull
    @Schema(examples = "true")
    private Boolean enabled;

    @NotNull
    @Schema(examples = "true")
    private Boolean sendInApp;
}
