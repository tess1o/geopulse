package org.github.tess1o.geopulse.admin.dto;

import lombok.Data;
import org.github.tess1o.geopulse.admin.model.Role;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class UpdateUserRoleRequest {
    @Schema(examples = "ADMIN")
    private Role role;
}
