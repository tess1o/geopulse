package org.github.tess1o.geopulse.admin.dto;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class UpdateUserStatusRequest {
    @Schema(examples = "false")
    private boolean active;
}
