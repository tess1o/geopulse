package org.github.tess1o.geopulse.admin.dto;

import lombok.Data;
import org.github.tess1o.geopulse.admin.model.Role;

@Data
public class UpdateUserRoleRequest {
    private Role role;
}
