package org.github.tess1o.geopulse.admin.dto;

import lombok.Builder;
import lombok.Data;
import org.github.tess1o.geopulse.admin.model.InvitationStatus;

@Data
@Builder
public class ValidateInvitationResponse {
    private boolean valid;
    private InvitationStatus status;
    private String message;
}
