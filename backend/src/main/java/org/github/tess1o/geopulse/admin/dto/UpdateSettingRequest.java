package org.github.tess1o.geopulse.admin.dto;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class UpdateSettingRequest {
    @Schema(examples = "auth.registration.enabled")
    private String key;
    @Schema(examples = "false")
    private String value;
}
