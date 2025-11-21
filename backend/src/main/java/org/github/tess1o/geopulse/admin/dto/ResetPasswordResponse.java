package org.github.tess1o.geopulse.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResetPasswordResponse {
    private String temporaryPassword;
}
